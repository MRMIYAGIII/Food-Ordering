package com.mugabo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.mugabo.repository.UserRepository;
import com.mugabo.service.FileStorageService;

@Controller
public class ProfileController {
    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/uploadProfilePicture")
    public String handleFileUpload(@RequestParam("profilePicture") MultipartFile file,
                                 @RequestParam(value = "userId") Long userId,
                                 RedirectAttributes redirectAttributes) {
        try {
            // Validate file
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("messageError", "Please select a file to upload");
                return "redirect:/dashboard";
            }

            // Validate file type
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                redirectAttributes.addFlashAttribute("messageError", "Please upload an image file");
                return "redirect:/dashboard";
            }

            // Validate file size (e.g., max 5MB)
            if (file.getSize() > 5 * 1024 * 1024) {
                redirectAttributes.addFlashAttribute("messageError", "File size should be less than 5MB");
                return "redirect:/dashboard";
            }

            // Save file and update user
            String savedFile = fileStorageService.saveFile(file);
            var userOptional = userRepository.findById(userId);
            
            if (userOptional.isPresent()) {
                var user = userOptional.get();
                // Delete old profile picture if exists
                if (user.getProfilePicture() != null) {
                    fileStorageService.deleteFile(user.getProfilePicture());
                }
                user.setProfilePicture(savedFile);
                userRepository.save(user);
                redirectAttributes.addFlashAttribute("messageSuccess", "Profile picture updated successfully!");
            } else {
                redirectAttributes.addFlashAttribute("messageError", "User not found!");
            }
        } catch (Exception e) {
            logger.error("Error uploading file", e);
            redirectAttributes.addFlashAttribute("messageError", "Failed to upload profile picture: " + e.getMessage());
        }
        return "redirect:/dashboard";
    }

    @GetMapping("/download-profile")
    public ResponseEntity<Resource> downloadFileFaster(@RequestParam("fileName") String filename) {
        try {
            var fileToDownload = fileStorageService.getDownloadFile(filename);
            String contentType = determineContentType(filename);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .contentLength(fileToDownload.length())
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(new FileSystemResource(fileToDownload));
        } catch (Exception e) {
            logger.error("Error downloading file", e);
            return ResponseEntity.notFound().build();
        }
    }

    private String determineContentType(String filename) {
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        switch (extension) {
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "gif":
                return "image/gif";
            default:
                return "application/octet-stream";
        }
    }
}