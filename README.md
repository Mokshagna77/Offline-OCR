# Offline OCR

An Android OCR application focused on **on-device text extraction without relying on a remote OCR service**.

## What it does

- Capture or select an image
- Extract text from the image on-device
- Support multiple OCR approaches through dedicated helpers
- Present the extracted text inside the Android application

## Architecture

```text
Camera / Image
      ↓
   OCR Manager
      ↓
 ┌───────────────┐
 │ ML Kit OCR    │
 │ Tesseract OCR │
 └───────────────┘
      ↓
Extracted Text
      ↓
Android UI
```

## Technical stack

- Kotlin
- Android SDK
- Google ML Kit Text Recognition
- Tesseract
- Gradle / Kotlin DSL

## Project structure

The OCR implementation is separated into focused components including `MLKitHelper`, `TesseractHelper`, `OcrManager`, and the main Android activity.

## Why I built it

This project explores **offline-first computer vision on Android** and the trade-offs between different OCR engines for local text extraction.

> Portfolio focus: **Computer Vision × Android × Edge / Offline AI**
