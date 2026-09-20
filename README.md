# File Explorer Pro

مستكشف ملفات احترافي لنظام أندرويد شبيه بـ ES File Explorer.

[![Build APK](https://github.com/Alhassani112/FileExplorerPro/actions/workflows/build.yml/badge.svg)](https://github.com/Alhassani112/FileExplorerPro/actions/workflows/build.yml)

## المميزات

- مستكشف ملفات كامل (نسخ، قص، لصق، حذف، إعادة تسمية، بحث، فرز)
- عرض بوسترات المجلدات تلقائياً من folder.jpg أو poster.png
- مشغل وسائط مدمج (Media3/ExoPlayer) مع إيماءات وسرعة تشغيل و PiP
- دعم USB OTG
- الوضع الليلي التلقائي مع Material 3
- عربي وإنجليزي مع دعم RTL
- عارض صور بتقريب لمسي

## المتطلبات

- أندرويد 7.0 (API 24) أو أحدث
- صلاحية MANAGE_EXTERNAL_STORAGE

## التحميل

### من GitHub Actions
1. افتح تبويب Actions
2. اختر آخر بناء ناجح
3. حمّل app-debug-apk من Artifacts

### من الإصدارات
راجع تبويب Releases لأحدث إصدار.

## البناء من المصدر

    git clone https://github.com/Alhassani112/FileExplorerPro.git
    cd FileExplorerPro
    ./gradlew assembleDebug

الإخراج: app/build/outputs/apk/debug/app-debug.apk

## البنية التقنية

- Kotlin + Jetpack Compose
- MVVM مع StateFlow
- Storage Access Framework (SAF)
- Media3/ExoPlayer للوسائط
- libaums لدعم USB OTG
- Coil للصور
- WorkManager للعمليات الطويلة

## الترخيص

مشروع مفتوح المصدر لأغراض تعليمية.
