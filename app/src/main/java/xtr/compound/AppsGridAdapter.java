package xtr.compound;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.PointerIcon;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import xtr.compound.databinding.AppWindowBinding;
import xtr.compound.server.RemoteServiceHelper;

public class AppsGridAdapter extends RecyclerView.Adapter<AppsGridAdapter.RecyclerViewHolder> {

    private final ArrayList<RecyclerData> appsDataArrayList = new ArrayList<>();
    private final Context context;
    @LayoutRes private final int resource;
    private final ViewGroup appsContainer;

    public AppsGridAdapter(Context context, @LayoutRes int appItemResource, ViewGroup appsContainer) {
        this.context = context;
        this.resource = appItemResource;
        this.appsContainer = appsContainer;

        PackageManager pm = context.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN, null);
        i.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> allApps = pm.queryIntentActivities(i, 0);

        for (ResolveInfo ri : allApps)
            appsDataArrayList.add(new RecyclerData(
                    ri.activityInfo.packageName,
                    ri.activityInfo.name,
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
            button.setIcon(recyclerData.icon.mutate());

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

            // launchIntent is null if app was uninstalled
            if (launchIntent != null) {
                launchIntent.setComponent(
                    new ComponentName(packageName, appsDataArrayList.get(i).className)
                );
            }

            AppWindowBinding binding = AppWindowBinding.inflate(LayoutInflater.from(context), appsContainer, true);
            binding.title.setOnTouchListener(new MovableFrameLayout(binding.getRoot()));
            binding.title.setOnClickListener(v -> {
                appsContainer.removeView(binding.getRoot());
                appsContainer.addView(binding.getRoot());
            });
            binding.title.setText(appsDataArrayList.get(i).title);
            binding.title.setIcon(appsDataArrayList.get(i).icon.mutate());

            binding.surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                VirtualDisplayHelper virtualDisplayHelper;
                @Override
                public void surfaceCreated(@NonNull SurfaceHolder holder) {
                    RemoteServiceHelper.getInstance(context, service -> {
                        virtualDisplayHelper = new VirtualDisplayHelper(
                                service,
                                binding.surfaceView.getWidth(),
                                binding.surfaceView.getHeight(),
                                holder.getSurface(),
                                launchIntent
                        );
                        binding.surfaceView.setOnTouchListener(virtualDisplayHelper);
                        binding.surfaceView.setOnGenericMotionListener(virtualDisplayHelper);
                        binding.closeButton.setOnClickListener(v -> {
                            virtualDisplayHelper.closeApp();
                            binding.getRoot().removeAllViews();
                            appsContainer.removeView(binding.getRoot());
                        });
                        binding.borderLeft.setOnTouchListener(new TouchToResizeListener(binding.getRoot(), TouchToResizeListener.HORIZONTAL));
                        binding.borderRight.setOnTouchListener(new TouchToResizeListener(binding.getRoot(), TouchToResizeListener.HORIZONTAL));
                        binding.borderBottom.setOnTouchListener(new TouchToResizeListener(binding.getRoot(), TouchToResizeListener.VERTICAL));

                        binding.borderBottom.setPointerIcon(PointerIcon.getSystemIcon(context, PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW));
                        binding.borderRight.setPointerIcon(PointerIcon.getSystemIcon(context, PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW));
                        binding.borderLeft.setPointerIcon(PointerIcon.getSystemIcon(context, PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW));
                    });

                }

                @Override
                public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                    if (virtualDisplayHelper != null) virtualDisplayHelper.resizeApp(width, height);
                }

                @Override
                public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

                }
            });
        }
    }

    private static class RecyclerData {
        public RecyclerData(String packageName, String className, CharSequence title, Drawable icon) {
            this.packageName = packageName;
            this.className = className;
            this.icon = icon;
            this.title = title;
        }


        private final String className;
        private final String packageName;
        private final CharSequence title;
        private final Drawable icon;
    }
}
