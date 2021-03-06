package android.view;

import android.hardware.display.DisplayManagerGlobal;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Trace;
import android.telephony.SubscriptionPlan;
import android.util.Jlog;
import android.util.Log;
import android.util.TimeUtils;
import java.io.PrintWriter;

public final class Choreographer {
    public static final int CALLBACK_ANIMATION = 1;
    public static final int CALLBACK_COMMIT = 3;
    public static final int CALLBACK_INPUT = 0;
    private static final int CALLBACK_LAST = 3;
    private static final String[] CALLBACK_TRACE_TITLES = new String[]{"input", "animation", "traversal", "commit"};
    public static final int CALLBACK_TRAVERSAL = 2;
    private static final boolean DEBUG_FRAMES = false;
    private static final boolean DEBUG_JANK = false;
    private static final long DEFAULT_FRAME_DELAY = 10;
    private static final Object FRAME_CALLBACK_TOKEN = new Object() {
        public String toString() {
            return "FRAME_CALLBACK_TOKEN";
        }
    };
    private static final int MSG_DO_FRAME = 0;
    private static final int MSG_DO_SCHEDULE_CALLBACK = 2;
    private static final int MSG_DO_SCHEDULE_VSYNC = 1;
    private static final int SKIPPED_FRAME_WARNING_LIMIT = SystemProperties.getInt("debug.choreographer.skipwarning", 30);
    private static final String TAG = "Choreographer";
    public static final long TOUNCH_RESPONSE_TIME_LIMIT = 500000000;
    private static final boolean USE_FRAME_TIME = SystemProperties.getBoolean("debug.choreographer.frametime", true);
    private static final boolean USE_VSYNC = SystemProperties.getBoolean("debug.choreographer.vsync", true);
    private static volatile Choreographer mMainInstance;
    private static volatile long sFrameDelay = DEFAULT_FRAME_DELAY;
    private static final ThreadLocal<Choreographer> sSfThreadInstance = new ThreadLocal<Choreographer>() {
        protected Choreographer initialValue() {
            Looper looper = Looper.myLooper();
            if (looper != null) {
                return new Choreographer(looper, 1, null);
            }
            throw new IllegalStateException("The current thread must have a looper!");
        }
    };
    private static final ThreadLocal<Choreographer> sThreadInstance = new ThreadLocal<Choreographer>() {
        protected Choreographer initialValue() {
            Looper looper = Looper.myLooper();
            if (looper != null) {
                Choreographer choreographer = new Choreographer(looper, 0, null);
                if (looper == Looper.getMainLooper()) {
                    Choreographer.mMainInstance = choreographer;
                }
                return choreographer;
            }
            throw new IllegalStateException("The current thread must have a looper!");
        }
    };
    protected boolean isNeedDraw;
    private int mCallBackCounts;
    private CallbackRecord mCallbackPool;
    private final CallbackQueue[] mCallbackQueues;
    private boolean mCallbacksRunning;
    private boolean mDebugPrintNextFrameTimeDelta;
    private final FrameDisplayEventReceiver mDisplayEventReceiver;
    private int mFPSDivisor;
    private long mFrameDelayTimeOnSync;
    FrameInfo mFrameInfo;
    private long mFrameIntervalNanos;
    private boolean mFrameScheduled;
    private final FrameHandler mHandler;
    private boolean mInDoframe;
    private long mLastFrameDoneTime;
    private long mLastFrameTimeNanos;
    private long mLastInputTime;
    private long mLastSkippedFrameEnd;
    private boolean mLastTraversal;
    private final Object mLock;
    private final Looper mLooper;
    private long mOldestInputTime;
    private long mRealFrameTime;

    private final class CallbackQueue {
        private CallbackRecord mHead;

        private CallbackQueue() {
        }

        /* synthetic */ CallbackQueue(Choreographer x0, AnonymousClass1 x1) {
            this();
        }

        public boolean hasDueCallbacksLocked(long now) {
            return this.mHead != null && this.mHead.dueTime <= now;
        }

        public CallbackRecord extractDueCallbacksLocked(long now) {
            CallbackRecord callbacks = this.mHead;
            if (callbacks == null || callbacks.dueTime > now) {
                return null;
            }
            CallbackRecord last = callbacks;
            CallbackRecord next = last.next;
            while (next != null) {
                if (next.dueTime > now) {
                    last.next = null;
                    break;
                }
                last = next;
                next = next.next;
            }
            this.mHead = next;
            return callbacks;
        }

        public void addCallbackLocked(long dueTime, Object action, Object token) {
            CallbackRecord callback = Choreographer.this.obtainCallbackLocked(dueTime, action, token);
            CallbackRecord entry = this.mHead;
            if (entry == null) {
                this.mHead = callback;
            } else if (dueTime < entry.dueTime) {
                callback.next = entry;
                this.mHead = callback;
            } else {
                while (entry.next != null) {
                    if (dueTime < entry.next.dueTime) {
                        callback.next = entry.next;
                        break;
                    }
                    entry = entry.next;
                }
                entry.next = callback;
            }
        }

        public void removeCallbacksLocked(Object action, Object token) {
            CallbackRecord predecessor = null;
            CallbackRecord callback = this.mHead;
            while (callback != null) {
                CallbackRecord next = callback.next;
                if ((action == null || callback.action == action) && (token == null || callback.token == token)) {
                    if (predecessor != null) {
                        predecessor.next = next;
                    } else {
                        this.mHead = next;
                    }
                    Choreographer.this.recycleCallbackLocked(callback);
                    Choreographer.this.mCallBackCounts = Choreographer.this.mCallBackCounts - 1;
                } else {
                    predecessor = callback;
                }
                callback = next;
            }
        }
    }

    private static final class CallbackRecord {
        public Object action;
        public long dueTime;
        public CallbackRecord next;
        public Object token;

        private CallbackRecord() {
        }

        /* synthetic */ CallbackRecord(AnonymousClass1 x0) {
            this();
        }

        public void run(long frameTimeNanos) {
            if (this.token == Choreographer.FRAME_CALLBACK_TOKEN) {
                ((FrameCallback) this.action).doFrame(frameTimeNanos);
            } else {
                ((Runnable) this.action).run();
            }
        }
    }

    public interface FrameCallback {
        void doFrame(long j);
    }

    private final class FrameDisplayEventReceiver extends DisplayEventReceiver implements Runnable {
        private int mFrame;
        private boolean mHavePendingVsync;
        private long mTimestampNanos;

        public FrameDisplayEventReceiver(Looper looper, int vsyncSource) {
            super(looper, vsyncSource);
        }

        public void onVsync(long timestampNanos, int builtInDisplayId, int frame) {
            if (builtInDisplayId != 0) {
                Log.d(Choreographer.TAG, "Received vsync from secondary display, but we don't support this case yet.  Choreographer needs a way to explicitly request vsync for a specific display to ensure it doesn't lose track of its scheduled vsync.");
                scheduleVsync();
                return;
            }
            long now = System.nanoTime();
            if (timestampNanos > now) {
                String str = Choreographer.TAG;
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("Frame time is ");
                stringBuilder.append(((float) (timestampNanos - now)) * 1.0E-6f);
                stringBuilder.append(" ms in the future!  Check that graphics HAL is generating vsync timestamps using the correct timebase.");
                Log.w(str, stringBuilder.toString());
                timestampNanos = now;
            }
            if (this.mHavePendingVsync) {
                Log.w(Choreographer.TAG, "Already have a pending vsync event.  There should only be one at a time.");
            } else {
                this.mHavePendingVsync = true;
            }
            this.mTimestampNanos = timestampNanos;
            this.mFrame = frame;
            Message msg = Message.obtain(Choreographer.this.mHandler, (Runnable) this);
            msg.setAsynchronous(true);
            msg.setVsync(true);
            Choreographer.this.mHandler.sendMessageAtTime(msg, timestampNanos / TimeUtils.NANOS_PER_MS);
        }

        public void run() {
            this.mHavePendingVsync = false;
            Choreographer.this.doFrame(this.mTimestampNanos, this.mFrame);
        }
    }

    private final class FrameHandler extends Handler {
        public FrameHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    Choreographer.this.doFrame(System.nanoTime(), 0);
                    return;
                case 1:
                    Choreographer.this.doScheduleVsync();
                    return;
                case 2:
                    Choreographer.this.doScheduleCallback(msg.arg1);
                    return;
                default:
                    return;
            }
        }
    }

    /* synthetic */ Choreographer(Looper x0, int x1, AnonymousClass1 x2) {
        this(x0, x1);
    }

    public void updateOldestInputTime(long inputTime) {
        if (inputTime < this.mOldestInputTime) {
            this.mOldestInputTime = inputTime;
        }
    }

    public void checkOldestInputTime() {
        if (!this.mInDoframe && this.mCallBackCounts <= 0) {
            this.mOldestInputTime = SubscriptionPlan.BYTES_UNLIMITED;
        }
    }

    public void checkTounchResponseTime(CharSequence title, long nowTime) {
        if (this.mOldestInputTime != SubscriptionPlan.BYTES_UNLIMITED && this.mLastInputTime != this.mOldestInputTime) {
            this.mLastInputTime = this.mOldestInputTime;
            long tounchResponseTime = nowTime - this.mOldestInputTime;
            if (tounchResponseTime >= TOUNCH_RESPONSE_TIME_LIMIT) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("#ARG1:<");
                stringBuilder.append(title);
                stringBuilder.append(">#ARG2:<");
                stringBuilder.append(tounchResponseTime / TimeUtils.NANOS_PER_MS);
                stringBuilder.append(">");
                Jlog.d(360, stringBuilder.toString());
            }
        }
    }

    private Choreographer(Looper looper, int vsyncSource) {
        FrameDisplayEventReceiver frameDisplayEventReceiver;
        this.mLock = new Object();
        this.mFPSDivisor = 1;
        this.mFrameInfo = new FrameInfo();
        this.mFrameDelayTimeOnSync = 0;
        this.mLastFrameDoneTime = 0;
        this.mRealFrameTime = 0;
        int i = 0;
        this.mLastTraversal = false;
        this.mLastSkippedFrameEnd = 0;
        this.isNeedDraw = false;
        this.mInDoframe = false;
        this.mCallBackCounts = 0;
        this.mOldestInputTime = SubscriptionPlan.BYTES_UNLIMITED;
        this.mLastInputTime = 0;
        this.mLooper = looper;
        this.mHandler = new FrameHandler(looper);
        if (USE_VSYNC) {
            frameDisplayEventReceiver = new FrameDisplayEventReceiver(looper, vsyncSource);
        } else {
            frameDisplayEventReceiver = null;
        }
        this.mDisplayEventReceiver = frameDisplayEventReceiver;
        this.mLastFrameTimeNanos = Long.MIN_VALUE;
        this.mFrameIntervalNanos = (long) (1.0E9f / getRefreshRate());
        this.mCallbackQueues = new CallbackQueue[4];
        while (true) {
            int i2 = i;
            if (i2 <= 3) {
                this.mCallbackQueues[i2] = new CallbackQueue(this, null);
                i = i2 + 1;
            } else {
                setFPSDivisor(SystemProperties.getInt(ThreadedRenderer.DEBUG_FPS_DIVISOR, 1));
                return;
            }
        }
    }

    private static float getRefreshRate() {
        return DisplayManagerGlobal.getInstance().getDisplayInfo(0).getMode().getRefreshRate();
    }

    public static Choreographer getInstance() {
        return (Choreographer) sThreadInstance.get();
    }

    public static Choreographer getSfInstance() {
        return (Choreographer) sSfThreadInstance.get();
    }

    public static Choreographer getMainThreadInstance() {
        return mMainInstance;
    }

    public static void releaseInstance() {
        Choreographer old = (Choreographer) sThreadInstance.get();
        sThreadInstance.remove();
        old.dispose();
    }

    private void dispose() {
        this.mDisplayEventReceiver.dispose();
    }

    public static long getFrameDelay() {
        return sFrameDelay;
    }

    public static void setFrameDelay(long frameDelay) {
        sFrameDelay = frameDelay;
    }

    public static long subtractFrameDelay(long delayMillis) {
        long frameDelay = sFrameDelay;
        return delayMillis <= frameDelay ? 0 : delayMillis - frameDelay;
    }

    public long getFrameIntervalNanos() {
        return this.mFrameIntervalNanos;
    }

    void dump(String prefix, PrintWriter writer) {
        String innerPrefix = new StringBuilder();
        innerPrefix.append(prefix);
        innerPrefix.append("  ");
        innerPrefix = innerPrefix.toString();
        writer.print(prefix);
        writer.println("Choreographer:");
        writer.print(innerPrefix);
        writer.print("mFrameScheduled=");
        writer.println(this.mFrameScheduled);
        writer.print(innerPrefix);
        writer.print("mLastFrameTime=");
        writer.println(TimeUtils.formatUptime(this.mLastFrameTimeNanos / TimeUtils.NANOS_PER_MS));
    }

    public void postCallback(int callbackType, Runnable action, Object token) {
        postCallbackDelayed(callbackType, action, token, 0);
    }

    public void postCallbackDelayed(int callbackType, Runnable action, Object token, long delayMillis) {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        } else if (callbackType < 0 || callbackType > 3) {
            throw new IllegalArgumentException("callbackType is invalid");
        } else {
            postCallbackDelayedInternal(callbackType, action, token, delayMillis);
        }
    }

    private void postCallbackDelayedInternal(int callbackType, Object action, Object token, long delayMillis) {
        synchronized (this.mLock) {
            long now = SystemClock.uptimeMillis();
            long dueTime = now + delayMillis;
            this.mCallbackQueues[callbackType].addCallbackLocked(dueTime, action, token);
            this.mCallBackCounts++;
            if (dueTime <= now) {
                scheduleFrameLocked(now);
            } else {
                Message msg = this.mHandler.obtainMessage(2, action);
                msg.arg1 = callbackType;
                msg.setAsynchronous(true);
                this.mHandler.sendMessageAtTime(msg, dueTime);
            }
        }
    }

    public void removeCallbacks(int callbackType, Runnable action, Object token) {
        if (callbackType < 0 || callbackType > 3) {
            throw new IllegalArgumentException("callbackType is invalid");
        }
        removeCallbacksInternal(callbackType, action, token);
    }

    private void removeCallbacksInternal(int callbackType, Object action, Object token) {
        synchronized (this.mLock) {
            this.mCallbackQueues[callbackType].removeCallbacksLocked(action, token);
            if (action != null && token == null) {
                this.mHandler.removeMessages(2, action);
            }
        }
    }

    public void postFrameCallback(FrameCallback callback) {
        postFrameCallbackDelayed(callback, 0);
    }

    public void postFrameCallbackDelayed(FrameCallback callback, long delayMillis) {
        if (callback != null) {
            postCallbackDelayedInternal(1, callback, FRAME_CALLBACK_TOKEN, delayMillis);
            return;
        }
        throw new IllegalArgumentException("callback must not be null");
    }

    public void removeFrameCallback(FrameCallback callback) {
        if (callback != null) {
            removeCallbacksInternal(1, callback, FRAME_CALLBACK_TOKEN);
            return;
        }
        throw new IllegalArgumentException("callback must not be null");
    }

    public long getFrameTime() {
        return getFrameTimeNanos() / TimeUtils.NANOS_PER_MS;
    }

    public long getFrameTimeNanos() {
        long nanoTime;
        synchronized (this.mLock) {
            if (this.mCallbacksRunning) {
                nanoTime = USE_FRAME_TIME ? this.mLastFrameTimeNanos : System.nanoTime();
            } else {
                throw new IllegalStateException("This method must only be called as part of a callback while a frame is in progress.");
            }
        }
        return nanoTime;
    }

    public long getLastFrameDoneTime() {
        return this.mLastFrameDoneTime;
    }

    public long getRealFrameTime() {
        return this.mRealFrameTime;
    }

    public long getDoFrameDelayTime() {
        return this.mFrameDelayTimeOnSync;
    }

    public boolean isLastTraversal() {
        return this.mLastTraversal;
    }

    public void setLastSkippedFrameEndTime(long endtime) {
        this.mLastSkippedFrameEnd = endtime;
    }

    public long getLastSkippedFrameEndTime() {
        return this.mLastSkippedFrameEnd;
    }

    public long getLastFrameTimeNanos() {
        long nanoTime;
        synchronized (this.mLock) {
            nanoTime = USE_FRAME_TIME ? this.mLastFrameTimeNanos : System.nanoTime();
        }
        return nanoTime;
    }

    private void scheduleFrameLocked(long now) {
        if (!this.mFrameScheduled) {
            this.mFrameScheduled = true;
            if (!USE_VSYNC) {
                long nextFrameTime = Math.max((this.mLastFrameTimeNanos / TimeUtils.NANOS_PER_MS) + sFrameDelay, now);
                Message msg = this.mHandler.obtainMessage(0);
                msg.setAsynchronous(true);
                this.mHandler.sendMessageAtTime(msg, nextFrameTime);
            } else if (isRunningOnLooperThreadLocked()) {
                scheduleVsyncLocked();
            } else {
                Message msg2 = this.mHandler.obtainMessage(1);
                msg2.setAsynchronous(true);
                this.mHandler.sendMessageAtFrontOfQueue(msg2);
            }
        }
    }

    void setFPSDivisor(int divisor) {
        if (divisor <= 0) {
            divisor = 1;
        }
        this.mFPSDivisor = divisor;
        ThreadedRenderer.setFPSDivisor(divisor);
    }

    /* JADX WARNING: Missing block: B:30:0x0088, code skipped:
            r4 = r7;
     */
    /* JADX WARNING: Missing block: B:32:?, code skipped:
            r1.isNeedDraw = false;
            r1.mInDoframe = true;
            android.os.Trace.traceBegin(8, "Choreographer#doFrame");
            r10 = android.util.TimeUtils.NANOS_PER_MS;
            android.view.animation.AnimationUtils.lockAnimationClock(r2 / android.util.TimeUtils.NANOS_PER_MS);
            r1.mFrameInfo.markInputHandlingStart();
            doCallbacks(0, r2);
            r1.mFrameInfo.markAnimationsStart();
            doCallbacks(1, r2);
            r1.mFrameInfo.markPerformTraversalsStart();
            doCallbacks(2, r2);
            doCallbacks(3, r2);
     */
    /* JADX WARNING: Missing block: B:35:0x00e0, code skipped:
            r10 = null;
            r1.mInDoframe = false;
            r1.mOldestInputTime = android.telephony.SubscriptionPlan.BYTES_UNLIMITED;
            android.view.animation.AnimationUtils.unlockAnimationClock();
            android.os.Trace.traceEnd(8);
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    void doFrame(long frameTimeNanos, int frame) {
        long frameTimeNanos2 = frameTimeNanos;
        synchronized (this.mLock) {
            if (this.mFrameScheduled) {
                long intendedFrameTimeNanos = frameTimeNanos2;
                long startNanos = System.nanoTime();
                long jitterNanos = startNanos - frameTimeNanos2;
                this.mRealFrameTime = frameTimeNanos2;
                this.mFrameDelayTimeOnSync = jitterNanos;
                this.mLastTraversal = false;
                if (jitterNanos >= this.mFrameIntervalNanos) {
                    long skippedFrames = jitterNanos / this.mFrameIntervalNanos;
                    Jlog.animationSkipFrames(skippedFrames);
                    if (skippedFrames >= ((long) SKIPPED_FRAME_WARNING_LIMIT)) {
                        String str = TAG;
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append("Skipped ");
                        stringBuilder.append(skippedFrames);
                        stringBuilder.append(" frames!  The application may be doing too much work on its main thread.");
                        Log.i(str, stringBuilder.toString());
                    }
                    frameTimeNanos2 = startNanos - (jitterNanos % this.mFrameIntervalNanos);
                }
                if (frameTimeNanos2 < this.mLastFrameTimeNanos) {
                    scheduleVsyncLocked();
                    return;
                }
                if (this.mFPSDivisor > 1) {
                    long timeSinceVsync = frameTimeNanos2 - this.mLastFrameTimeNanos;
                    if (timeSinceVsync < this.mFrameIntervalNanos * ((long) this.mFPSDivisor) && timeSinceVsync > 0) {
                        scheduleVsyncLocked();
                        return;
                    }
                }
                this.mFrameInfo.setVsync(intendedFrameTimeNanos, frameTimeNanos2);
                this.mFrameScheduled = false;
                this.mLastFrameTimeNanos = frameTimeNanos2;
            } else {
                return;
            }
        }
        this.mLastFrameDoneTime = System.nanoTime();
        Looper.myQueue().setLastFrameDoneTime(this.mLastFrameDoneTime / r10);
    }

    /* JADX WARNING: Missing block: B:17:0x0045, code skipped:
            r3 = r0;
     */
    /* JADX WARNING: Missing block: B:19:?, code skipped:
            android.os.Trace.traceBegin(8, CALLBACK_TRACE_TITLES[r2]);
            r0 = r3;
     */
    /* JADX WARNING: Missing block: B:20:0x004f, code skipped:
            if (r0 == null) goto L_0x0062;
     */
    /* JADX WARNING: Missing block: B:22:0x0052, code skipped:
            if (r2 != 2) goto L_0x005b;
     */
    /* JADX WARNING: Missing block: B:24:0x0056, code skipped:
            if (r0.next != null) goto L_0x005b;
     */
    /* JADX WARNING: Missing block: B:25:0x0058, code skipped:
            r1.mLastTraversal = true;
     */
    /* JADX WARNING: Missing block: B:26:0x005b, code skipped:
            r0.run(r10);
     */
    /* JADX WARNING: Missing block: B:27:0x0060, code skipped:
            r0 = r0.next;
     */
    /* JADX WARNING: Missing block: B:28:0x0062, code skipped:
            r5 = r1.mLock;
     */
    /* JADX WARNING: Missing block: B:29:0x0064, code skipped:
            monitor-enter(r5);
     */
    /* JADX WARNING: Missing block: B:31:?, code skipped:
            r1.mCallbacksRunning = false;
     */
    /* JADX WARNING: Missing block: B:32:0x0067, code skipped:
            r0 = r3.next;
            recycleCallbackLocked(r3);
            r3 = r0;
            r1.mCallBackCounts--;
     */
    /* JADX WARNING: Missing block: B:33:0x0073, code skipped:
            if (r3 != null) goto L_0x0067;
     */
    /* JADX WARNING: Missing block: B:34:0x0075, code skipped:
            monitor-exit(r5);
     */
    /* JADX WARNING: Missing block: B:35:0x0076, code skipped:
            android.os.Trace.traceEnd(8);
     */
    /* JADX WARNING: Missing block: B:36:0x007a, code skipped:
            return;
     */
    /* JADX WARNING: Missing block: B:43:0x0081, code skipped:
            monitor-enter(r1.mLock);
     */
    /* JADX WARNING: Missing block: B:45:?, code skipped:
            r1.mCallbacksRunning = false;
     */
    /* JADX WARNING: Missing block: B:46:0x0084, code skipped:
            r4 = r3.next;
            recycleCallbackLocked(r3);
            r3 = r4;
            r1.mCallBackCounts--;
     */
    /* JADX WARNING: Missing block: B:47:0x0090, code skipped:
            if (r3 != null) goto L_0x0092;
     */
    /* JADX WARNING: Missing block: B:50:0x0094, code skipped:
            android.os.Trace.traceEnd(8);
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    void doCallbacks(int callbackType, long frameTimeNanos) {
        Throwable th;
        int i = callbackType;
        synchronized (this.mLock) {
            long frameTimeNanos2;
            try {
                long now = System.nanoTime();
                CallbackRecord callbacks = this.mCallbackQueues[i].extractDueCallbacksLocked(now / TimeUtils.NANOS_PER_MS);
                if (callbacks == null) {
                    return;
                }
                this.mCallbacksRunning = true;
                if (i == 3) {
                    long jitterNanos = now - frameTimeNanos;
                    Trace.traceCounter(8, "jitterNanos", (int) jitterNanos);
                    if (jitterNanos >= 2 * this.mFrameIntervalNanos) {
                        frameTimeNanos2 = now - ((jitterNanos % this.mFrameIntervalNanos) + this.mFrameIntervalNanos);
                        try {
                            this.mLastFrameTimeNanos = frameTimeNanos2;
                        } catch (Throwable th2) {
                            th = th2;
                            throw th;
                        }
                    }
                }
                frameTimeNanos2 = frameTimeNanos;
            } catch (Throwable th3) {
                th = th3;
                frameTimeNanos2 = frameTimeNanos;
                throw th;
            }
        }
    }

    void doScheduleVsync() {
        synchronized (this.mLock) {
            if (this.mFrameScheduled) {
                scheduleVsyncLocked();
            }
        }
    }

    void doScheduleCallback(int callbackType) {
        synchronized (this.mLock) {
            if (!this.mFrameScheduled) {
                long now = SystemClock.uptimeMillis();
                if (this.mCallbackQueues[callbackType].hasDueCallbacksLocked(now)) {
                    scheduleFrameLocked(now);
                }
            }
        }
    }

    private void scheduleVsyncLocked() {
        this.mDisplayEventReceiver.scheduleVsync();
    }

    private boolean isRunningOnLooperThreadLocked() {
        return Looper.myLooper() == this.mLooper;
    }

    private CallbackRecord obtainCallbackLocked(long dueTime, Object action, Object token) {
        CallbackRecord callback = this.mCallbackPool;
        if (callback == null) {
            callback = new CallbackRecord();
        } else {
            this.mCallbackPool = callback.next;
            callback.next = null;
        }
        callback.dueTime = dueTime;
        callback.action = action;
        callback.token = token;
        return callback;
    }

    private void recycleCallbackLocked(CallbackRecord callback) {
        callback.action = null;
        callback.token = null;
        callback.next = this.mCallbackPool;
        this.mCallbackPool = callback;
    }
}
