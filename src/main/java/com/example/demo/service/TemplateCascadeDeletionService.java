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
    @Autowired(required = false) private FirebaseStorageService storageService;
    @Autowired private Firestore db;
    @Autowired private TemplateGroupService templateGroupService;

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
            // 1a) Example video + thumbnail via VideoDao
            try {
                String videoId = tpl.getVideoId();
                if (videoId != null && !videoId.isBlank()) {
                    var video = videoDao.getVideoById(videoId);
                    if (video != null) {
                        storageService.deleteObjectByUrl(video.getUrl());
                        storageService.deleteObjectByUrl(video.getThumbnailUrl());
                        try {
                            boolean removed = videoDao.deleteVideoById(videoId);
                            if (!removed) System.err.println("[CASCADE] Example video doc not found or not removed: " + videoId);
                        } catch (Exception ignore) {}
                    }
                }
            } catch (Exception e) {
                System.err.println("[CASCADE] Example video delete warn: " + e);
            }

            // 1b) Keyframes from template scenes
            try {
                if (tpl.getScenes() != null) {
                    for (Scene s : tpl.getScenes()) {
                        if (s.getKeyframeUrl() != null && !s.getKeyframeUrl().isBlank()) {
                            storageService.deleteObjectByUrl(s.getKeyframeUrl());
                        }
                        if (s.getBlockImageUrls() != null) {
                            for (String url : s.getBlockImageUrls().values()) {
                                storageService.deleteObjectByUrl(url);
                            }
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
            System.err.println("[CASCADE] Storage hard-delete enabled but FirebaseStorageService unavailable; skipping storage deletion.");
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

        // 2c) remove from groups
        try {
            templateGroupService.removeTemplateFromGroups(templateId);
        } catch (Exception e) {
            System.err.println("[CASCADE] group cleanup warn: " + e);
        }

        // 2d) delete template doc
        boolean ok = templateDao.deleteTemplate(templateId);
        if (!ok) throw new NoSuchElementException("Template not found on delete: " + templateId);
    }
}
