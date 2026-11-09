package xtr.compound.server;

import android.os.Parcel;
import android.os.Parcelable;

/** Small, stable task description crossing the privileged service AIDL boundary. */
public final class RunningTask implements Parcelable {
    public final int taskId;
    public final int displayId;
    public final String packageName;
    public final String title;
    public final boolean compoundOwned;

    public RunningTask(int taskId, int displayId, String packageName, String title, boolean compoundOwned) {
        this.taskId = taskId;
        this.displayId = displayId;
        this.packageName = packageName;
        this.title = title;
        this.compoundOwned = compoundOwned;
    }

    private RunningTask(Parcel in) {
        taskId = in.readInt();
        displayId = in.readInt();
        packageName = in.readString();
        title = in.readString();
        compoundOwned = in.readInt() != 0;
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(taskId);
        out.writeInt(displayId);
        out.writeString(packageName);
        out.writeString(title);
        out.writeInt(compoundOwned ? 1 : 0);
    }

    @Override public int describeContents() { return 0; }

    public static final Creator<RunningTask> CREATOR = new Creator<RunningTask>() {
        @Override public RunningTask createFromParcel(Parcel in) { return new RunningTask(in); }
        @Override public RunningTask[] newArray(int size) { return new RunningTask[size]; }
    };
}
