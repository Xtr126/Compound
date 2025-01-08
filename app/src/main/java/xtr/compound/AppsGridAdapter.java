package xtr.compound;

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

    private final ArrayList<RecyclerData> appsDataArrayList = new ArrayList<>();
    private final Context context;
    @LayoutRes private final int resource;

    public AppsGridAdapter(Context context, @LayoutRes int resource) {
        this.context = context;
        this.resource = resource;
        PackageManager pm = context.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN, null);
        i.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> allApps = pm.queryIntentActivities(i, 0);

        for (ResolveInfo ri : allApps)
            appsDataArrayList.add(new RecyclerData(
                    ri.activityInfo.packageName,
                    ri.loadLabel(pm),
                    ri.activityInfo.loadIcon(pm)));
    }

    @NonNull
    @Override
    public RecyclerViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // Inflate Layout
        View view = LayoutInflater.from(parent.getContext()).inflate(resource, parent, false);
        return new RecyclerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerViewHolder holder, int position) {
        // Set the icon and text for the button from data.
        RecyclerData recyclerData = appsDataArrayList.get(position);
        if (holder.itemView instanceof MaterialButton) {
            MaterialButton button = (MaterialButton) holder.itemView;
            button.setIcon(recyclerData.icon);

            if (resource != R.layout.app_taskbar_item) {
                button.setText(recyclerData.title);
            } else {
                button.setTooltipText(recyclerData.title);
            }
        }
    }

    @Override
    public int getItemCount() {
        // this method returns the size of recyclerview
        return appsDataArrayList.size();
    }

    // View Holder Class to handle Recycler View.
    public class RecyclerViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        public RecyclerViewHolder(View view) {
            super(view);
            view.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int i = getAdapterPosition();
            String packageName = appsDataArrayList.get(i).packageName;
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            context.startActivity(launchIntent);
        }
    }

    private static class RecyclerData {
        public RecyclerData(String packageName, CharSequence title, Drawable icon) {
            this.packageName = packageName;
            this.icon = icon;
            this.title = title;
        }

        String packageName;
        CharSequence title;
        Drawable icon;
    }
}
