package com.ming.imfileservice.interfaces.http;

import com.ming.imapicontract.file.FileApiPaths;
import com.ming.imfileservice.file.FileAccessDeniedException;
import com.ming.imfileservice.file.FileNotFoundBizException;
import com.ming.imfileservice.file.StoredFileResource;
import com.ming.imfileservice.service.FileService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 签名下载控制器。
 */
@RestController
public class FileDownloadController {

    private final FileService fileService;

    public FileDownloadController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping(FileApiPaths.SIGNED_DOWNLOAD)
    public ResponseEntity<?> signedDownload(@RequestParam("fileId") String fileId,
                                            @RequestParam("exp") long expireAt,
                                            @RequestParam("sig") String signature) {
        try {
            StoredFileResource resource = fileService.loadBySignedDownloadUrl(fileId, expireAt, signature);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(resource.contentType()));
            headers.setContentLength(resource.size());
            headers.setContentDisposition(ContentDisposition.inline().filename(resource.fileName()).build());
            return ResponseEntity.ok().headers(headers).body(new FileSystemResource(resource.path()));
        } catch (FileAccessDeniedException ex) {
            return ResponseEntity.status(403).body("forbidden");
        } catch (FileNotFoundBizException ex) {
            return ResponseEntity.status(404).body("not found");
        }
    }
}
