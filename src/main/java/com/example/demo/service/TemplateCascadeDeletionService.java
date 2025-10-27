package com.example.demo.service;

import com.example.demo.dao.TemplateDao;
import com.example.demo.dao.SceneSubmissionDao;
import com.example.demo.dao.VideoDao;
import com.example.demo.model.ManualTemplate;
import com.example.demo.model.Scene;
import com.example.demo.model.SceneSubmission;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

@Service
public class TemplateCascadeDeletionService {

    @Autowired private TemplateDao templateDao;
    @Autowired private SceneSubmissionDao sceneSubmissionDao;
    @Autowired private VideoDao videoDao;
    @Autowired(required = false) private com.example.demo.service.AlibabaOssStorageService storageService;
    @Autowired(required = false) private com.example.demo.dao.TemplateAssignmentDao templateAssignmentDao;
    @Autowired private Firestore db;


    @Value("${deletion.cascade.enabled:true}")
    private boolean cascadeEnabled;

    @Value("${deletion.hardDelete.storage.enabled:true}")
    private boolean hardDeleteStorage;

    public void deleteTemplateAssetsAndDocs(String templateId) throws Exception {
        if (!cascadeEnabled) {
            // Fallback to legacy: just delete template doc
            boolean ok = templateDao.deleteTemplate(templateId);
            if (!ok) throw new NoSuchElementException("Template not found: " + templateId);
            return;
        }

        ManualTemplate tpl = templateDao.getTemplate(templateId);
        if (tpl == null) throw new NoSuchElementException("Template not found: " + templateId);

        // 1) Storage first
        if (hardDeleteStorage && storageService != null) {
            // 1a) Template-level example video (AI templates only)
            try {
                String videoId = tpl.getVideoId();
                if (videoId != null && !videoId.isBlank()) {
                    var video = videoDao.getVideoById(videoId);
                    if (video != null) {
                        storageService.deleteObjectByUrl(video.getUrl());
                        storageService.deleteObjectByUrl(video.getThumbnailUrl());
                        try {
                            boolean removed = videoDao.deleteVideoById(videoId);
                            if (!removed) System.err.println("[CASCADE] Template example video doc not found or not removed: " + videoId);
                        } catch (Exception ignore) {}
                    }
                }
            } catch (Exception e) {
                System.err.println("[CASCADE] Template example video delete warn: " + e);
            }
            
            // 1a-2) Scene-level example videos (Manual templates)
            // Manual templates have one example video per scene
            try {
                if (tpl.getScenes() != null) {
                    for (Scene s : tpl.getScenes()) {
                        String sceneVideoId = s.getVideoId();
                        if (sceneVideoId != null && !sceneVideoId.isBlank()) {
                            var sceneVideo = videoDao.getVideoById(sceneVideoId);
                            if (sceneVideo != null) {
                                storageService.deleteObjectByUrl(sceneVideo.getUrl());
                                storageService.deleteObjectByUrl(sceneVideo.getThumbnailUrl());
                                try {
                                    boolean removed = videoDao.deleteVideoById(sceneVideoId);
                                    if (!removed) System.err.println("[CASCADE] Scene example video doc not found or not removed: " + sceneVideoId);
                                } catch (Exception ignore) {}
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[CASCADE] Scene example videos delete warn: " + e);
            }

            // 1b) Keyframes from template scenes
            try {
                if (tpl.getScenes() != null) {
                    for (Scene s : tpl.getScenes()) {
                        if (s.getKeyframeUrl() != null && !s.getKeyframeUrl().isBlank()) {
                            storageService.deleteObjectByUrl(s.getKeyframeUrl());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[CASCADE] Keyframes delete warn: " + e);
            }

            // 1c) Scene submissions assets
            try {
                List<SceneSubmission> subs = sceneSubmissionDao.findByTemplateId(templateId);
                for (SceneSubmission sub : subs) {
                    storageService.deleteObjectByUrl(sub.getVideoUrl());
                    storageService.deleteObjectByUrl(sub.getThumbnailUrl());
                }
            } catch (Exception e) {
                System.err.println("[CASCADE] Submission assets delete warn: " + e);
            }
        } else if (hardDeleteStorage && storageService == null) {
            System.err.println("[CASCADE] Storage hard-delete enabled but AlibabaOssStorageService unavailable; skipping storage deletion.");
        }

        // 2) Firestore docs
        // 2a) sceneSubmissions
        try {
            sceneSubmissionDao.deleteScenesByTemplateId(templateId);
        } catch (ExecutionException | InterruptedException e) {
            throw e;
        }

        // 2b) submittedVideos docs
        try {
            Query q = db.collection("submittedVideos").whereEqualTo("templateId", templateId);
            for (DocumentSnapshot snap : q.get().get().getDocuments()) {
                snap.getReference().delete();
            }
        } catch (Exception e) {
            System.err.println("[CASCADE] submittedVideos delete warn: " + e);
        }

        // 2c) remove template assignments (new system)
        try {
            // Delete all assignments for this template
            if (templateAssignmentDao != null) {
                templateAssignmentDao.deleteAssignmentsByTemplate(templateId);
            }
        } catch (Exception e) {
            System.err.println("[CASCADE] Template assignments delete warn: " + e);
        }

        // 2d) delete template doc
        boolean ok = templateDao.deleteTemplate(templateId);
        if (!ok) throw new NoSuchElementException("Template not found on delete: " + templateId);
    }
}
