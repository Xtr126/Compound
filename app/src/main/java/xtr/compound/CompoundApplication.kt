package xtr.compound

import android.app.Application
import com.topjohnwu.superuser.Shell

/** Configure libsu before any Activity, service helper, or instrumentation opens the main shell. */
class CompoundApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(Shell.Builder.create().setTimeout(10))
    }
}
