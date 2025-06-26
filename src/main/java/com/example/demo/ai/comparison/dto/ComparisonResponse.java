package com.example.demo.ai.comparison.dto;

import com.example.demo.ai.comparison.ComparisonResult;

public class ComparisonResponse {
    private boolean success;
    private String message;
    private ComparisonResult result;
    private String detailedReport;

    public ComparisonResponse() {
    }

    public ComparisonResponse(boolean success, String message, ComparisonResult result, String detailedReport) {
        this.success = success;
        this.message = message;
        this.result = result;
        this.detailedReport = detailedReport;
    }

    public static ComparisonResponse success(ComparisonResult result, String report) {
        return new ComparisonResponse(true, "Comparison completed successfully", result, report);
    }

    public static ComparisonResponse error(String message) {
        return new ComparisonResponse(false, message, null, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ComparisonResult getResult() {
        return result;
    }

    public void setResult(ComparisonResult result) {
        this.result = result;
    }

    public String getDetailedReport() {
        return detailedReport;
    }

    public void setDetailedReport(String detailedReport) {
        this.detailedReport = detailedReport;
    }
}