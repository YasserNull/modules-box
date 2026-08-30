package com.yassernull.nullbox.data.model

// يمثل وحدة (Module) مثبتة محليًا.
data class Module(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: String?,
    val author: String,
    val description: String?,
    val path: String,       // المسار إلى مجلد الوحدة على الجهاز.
    val repository: String?, // رابط مستودع GitHub الخاص بالوحدة.
    val html: String?,      // مسار ملف HTML داخل الوحدة (نسبي لمجلدها) يتم فتحه عند الضغط على الوحدة.
    val install: String?,   // مسار سكربت التثبيت (install_script.sh) داخل الوحدة.
    val permission: String?, // صلاحية الوحدة: default, shizuku, root.
    val icon: String?       // مسار أيقونة الوحدة (icon.png, icon.jpg, icon.svg).
)
