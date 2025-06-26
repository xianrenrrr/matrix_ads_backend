# Task 2: Video Comparison API

This document explains how to use the Video Comparison API endpoints in your frontend application.

## 🚀 **Available API Endpoints**

### 1. **Compare Video to Template**
```http
POST /api/comparison/compare
Content-Type: application/json

{
  "templateId": "template_123",
  "userVideoId": "video_456"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Comparison completed successfully",
  "result": {
    "overallScore": 0.789,
    "sceneComparisons": [
      {
        "sceneIndex": 0,
        "similarity": 0.856,
        "blockScores": {
          "0_0": 0.92,
          "0_1": 0.84,
          "0_2": 0.78,
          "1_0": 0.91,
          "1_1": 0.89,
          "1_2": 0.73,
          "2_0": 0.85,
          "2_1": 0.88,
          "2_2": 0.80
        }
      }
    ]
  },
  "detailedReport": "=== VIDEO COMPARISON REPORT ===\n\nOverall Similarity Score: 78.9%\n..."
}
```

### 2. **Quick Similarity Score** (Lightweight)
```http
GET /api/comparison/score?templateId=template_123&userVideoId=video_456
```

**Response:**
```json
{
  "success": true,
  "overallScore": 0.789,
  "scorePercentage": 79,
  "scenesCompared": 3
}
```

### 3. **Compare Two Templates**
```http
POST /api/comparison/templates?template1Id=template_123&template2Id=template_456
```

## 🎯 **Frontend Integration Examples**

### **Angular Service Example**

```typescript
// video-comparison.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class VideoComparisonService {
  private baseUrl = 'http://localhost:8080/api/comparison';

  constructor(private http: HttpClient) {}

  compareVideoToTemplate(templateId: string, userVideoId: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/compare`, {
      templateId,
      userVideoId
    });
  }

  getQuickScore(templateId: string, userVideoId: string): Observable<any> {
    return this.http.get(`${this.baseUrl}/score`, {
      params: { templateId, userVideoId }
    });
  }

  compareTemplates(template1Id: string, template2Id: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/templates`, null, {
      params: { template1Id, template2Id }
    });
  }
}
```

### **Component Usage Example**

```typescript
// video-review.component.ts
export class VideoReviewComponent {
  comparisonResult: any = null;
  loading = false;

  constructor(private comparisonService: VideoComparisonService) {}

  compareVideo(templateId: string, userVideoId: string) {
    this.loading = true;
    
    this.comparisonService.compareVideoToTemplate(templateId, userVideoId)
      .subscribe({
        next: (response) => {
          if (response.success) {
            this.comparisonResult = response.result;
            this.displayResults();
          } else {
            console.error('Comparison failed:', response.message);
          }
          this.loading = false;
        },
        error: (error) => {
          console.error('API Error:', error);
          this.loading = false;
        }
      });
  }

  displayResults() {
    const overall = Math.round(this.comparisonResult.overallScore * 100);
    console.log(`Overall similarity: ${overall}%`);
    
    // Display scene-by-scene results
    this.comparisonResult.sceneComparisons.forEach((scene, index) => {
      const sceneScore = Math.round(scene.similarity * 100);
      console.log(`Scene ${index + 1}: ${sceneScore}%`);
    });
  }

  getScoreColor(score: number): string {
    if (score >= 0.8) return 'green';
    if (score >= 0.6) return 'orange';
    return 'red';
  }
}
```

### **Template Usage Example**

```html
<!-- video-review.component.html -->
<div class="comparison-container">
  <h3>Video Comparison Results</h3>
  
  <div *ngIf="loading" class="loading">
    Analyzing video similarity...
  </div>
  
  <div *ngIf="comparisonResult && !loading" class="results">
    <!-- Overall Score -->
    <div class="overall-score">
      <h4>Overall Similarity</h4>
      <div class="score-circle" [style.color]="getScoreColor(comparisonResult.overallScore)">
        {{ (comparisonResult.overallScore * 100) | number:'1.0-0' }}%
      </div>
    </div>
    
    <!-- Scene Breakdown -->
    <div class="scene-breakdown">
      <h4>Scene-by-Scene Analysis</h4>
      <div *ngFor="let scene of comparisonResult.sceneComparisons; let i = index" 
           class="scene-result">
        <h5>Scene {{ i + 1 }}</h5>
        <div class="scene-score" [style.color]="getScoreColor(scene.similarity)">
          {{ (scene.similarity * 100) | number:'1.0-0' }}%
        </div>
        
        <!-- Block Grid Visualization -->
        <div class="block-grid">
          <div *ngFor="let block of getBlockArray(scene.blockScores)" 
               class="block"
               [style.background-color]="getBlockColor(block.score)">
            {{ (block.score * 100) | number:'1.0-0' }}%
          </div>
        </div>
      </div>
    </div>
  </div>
</div>
```

## 🔧 **Where to Place in Your App**

### **Content Creator Dashboard**
```typescript
// content-creator-dashboard.component.ts
export class ContentCreatorDashboardComponent {
  
  submitVideoForReview(videoId: string, templateId: string) {
    // First, get quick similarity score
    this.comparisonService.getQuickScore(templateId, videoId)
      .subscribe(response => {
        if (response.scorePercentage >= 70) {
          this.showSuccess('Great! Your video matches the template well.');
        } else {
          this.showWarning('Consider reviewing the template guidelines.');
        }
      });
  }
}
```

### **Content Manager Review**
```typescript
// content-manager-review.component.ts
export class ContentManagerReviewComponent {
  
  reviewSubmission(submissionId: string) {
    // Get detailed comparison for manager review
    this.comparisonService.compareVideoToTemplate(templateId, videoId)
      .subscribe(response => {
        this.showDetailedReport(response.detailedReport);
        this.showSceneBreakdown(response.result.sceneComparisons);
      });
  }
}
```

## 📊 **Recommended UI Placements**

1. **Video Upload Flow**: Quick score after upload
2. **Review Dashboard**: Detailed comparison results
3. **Template Management**: Template vs template comparison
4. **Analytics**: Aggregate similarity scores over time

## ⚠️ **Important Notes**

- **Integration Needed**: The current implementation uses example data
- **TODO**: Connect `VideoComparisonIntegrationService` with your actual Template and Video DAOs
- **Performance**: Use the `/score` endpoint for quick checks, full `/compare` for detailed analysis
- **Error Handling**: Always check the `success` field in responses

The API is ready to use once you complete the DAO integration! 🎯