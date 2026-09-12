# ProGuard rules for OfflineTester

# Keep all classes in our package
-keep class com.gag4.offlinetester.** { *; }

# Keep Android classes
-keep class android.nfc.** { *; }
-keep class android.nfc.cardemulation.** { *; }

# Keep ViewModel and LiveData
-keep class androidx.lifecycle.ViewModel { *; }
-keep class androidx.lifecycle.LiveData { *; }
-keep class androidx.lifecycle.MutableLiveData { *; }

# Keep RecyclerView adapter
-keep class androidx.recyclerview.widget.RecyclerView { *; }
-keep class androidx.recyclerview.widget.RecyclerView$Adapter { *; }

# Don't warn about missing classes
-dontwarn com.google.android.material.**
-dontwarn androidx.**
