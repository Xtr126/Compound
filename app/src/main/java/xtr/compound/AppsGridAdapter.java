package xtr.compound;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class AppsGridAdapter extends RecyclerView.Adapter<AppsGridAdapter.RecyclerViewHolder> {
    private final ArrayList<RecyclerData> apps = new ArrayList<>();
    private final Context context;
    @LayoutRes private final int resource;
    private final DesktopWindowController windows;

    public AppsGridAdapter(Context context, @LayoutRes int appItemResource, DesktopWindowController windows) {
        this.context = context;
        this.resource = appItemResource;
        this.windows = windows;
        PackageManager pm = context.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(query, 0);
        for (ResolveInfo info : resolved) {
            if (context.getPackageName().equals(info.activityInfo.packageName)) continue;
            apps.add(new RecyclerData(info.activityInfo.packageName, info.activityInfo.name,
                    info.loadLabel(pm), info.activityInfo.loadIcon(pm)));
        }
    }

    @NonNull @Override
    public RecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new RecyclerViewHolder(LayoutInflater.from(parent.getContext()).inflate(resource, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull RecyclerViewHolder holder, int position) {
        RecyclerData app = apps.get(position);
        MaterialButton button = (MaterialButton) holder.itemView;
        button.setIcon(app.icon.mutate());
        if (resource != R.layout.app_taskbar_item) button.setText(app.title);
        else button.setTooltipText(app.title);
    }

    @Override public int getItemCount() { return apps.size(); }

    public final class RecyclerViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        RecyclerViewHolder(View view) { super(view); view.setOnClickListener(this); }
        @Override public void onClick(View view) {
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;
            RecyclerData app = apps.get(position);
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(app.packageName);
            if (intent == null) return;
            intent.setComponent(new ComponentName(app.packageName, app.className));
            windows.launch(intent, app.title, app.icon);
        }
    }

    private static final class RecyclerData {
        final String packageName;
        final String className;
        final CharSequence title;
        final Drawable icon;
        RecyclerData(String packageName, String className, CharSequence title, Drawable icon) {
            this.packageName = packageName;
            this.className = className;
            this.title = title;
            this.icon = icon;
        }
    }
}
