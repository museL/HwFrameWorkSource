package android.transition;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Path;
import android.graphics.Rect;
import android.telephony.SubscriptionPlan;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.util.SparseLongArray;
import android.view.InflateException;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowId;
import android.view.animation.AnimationUtils;
import android.widget.ListView;
import com.android.internal.R;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

public abstract class Transition implements Cloneable {
    static final boolean DBG = false;
    private static final int[] DEFAULT_MATCH_ORDER = new int[]{2, 1, 3, 4};
    private static final String LOG_TAG = "Transition";
    private static final int MATCH_FIRST = 1;
    public static final int MATCH_ID = 3;
    private static final String MATCH_ID_STR = "id";
    public static final int MATCH_INSTANCE = 1;
    private static final String MATCH_INSTANCE_STR = "instance";
    public static final int MATCH_ITEM_ID = 4;
    private static final String MATCH_ITEM_ID_STR = "itemId";
    private static final int MATCH_LAST = 4;
    public static final int MATCH_NAME = 2;
    private static final String MATCH_NAME_STR = "name";
    private static final String MATCH_VIEW_NAME_STR = "viewName";
    private static final PathMotion STRAIGHT_PATH_MOTION = new PathMotion() {
        public Path getPath(float startX, float startY, float endX, float endY) {
            Path path = new Path();
            path.moveTo(startX, startY);
            path.lineTo(endX, endY);
            return path;
        }
    };
    private static ThreadLocal<ArrayMap<Animator, AnimationInfo>> sRunningAnimators = new ThreadLocal();
    ArrayList<Animator> mAnimators = new ArrayList();
    boolean mCanRemoveViews = false;
    private ArrayList<Animator> mCurrentAnimators = new ArrayList();
    long mDuration = -1;
    private TransitionValuesMaps mEndValues = new TransitionValuesMaps();
    ArrayList<TransitionValues> mEndValuesList;
    private boolean mEnded = false;
    EpicenterCallback mEpicenterCallback;
    TimeInterpolator mInterpolator = null;
    ArrayList<TransitionListener> mListeners = null;
    int[] mMatchOrder = DEFAULT_MATCH_ORDER;
    private String mName = getClass().getName();
    ArrayMap<String, String> mNameOverrides;
    int mNumInstances = 0;
    TransitionSet mParent = null;
    PathMotion mPathMotion = STRAIGHT_PATH_MOTION;
    boolean mPaused = false;
    TransitionPropagation mPropagation;
    ViewGroup mSceneRoot = null;
    long mStartDelay = -1;
    private TransitionValuesMaps mStartValues = new TransitionValuesMaps();
    ArrayList<TransitionValues> mStartValuesList;
    ArrayList<View> mTargetChildExcludes = null;
    ArrayList<View> mTargetExcludes = null;
    ArrayList<Integer> mTargetIdChildExcludes = null;
    ArrayList<Integer> mTargetIdExcludes = null;
    ArrayList<Integer> mTargetIds = new ArrayList();
    ArrayList<String> mTargetNameExcludes = null;
    ArrayList<String> mTargetNames = null;
    ArrayList<Class> mTargetTypeChildExcludes = null;
    ArrayList<Class> mTargetTypeExcludes = null;
    ArrayList<Class> mTargetTypes = null;
    ArrayList<View> mTargets = new ArrayList();

    public static class AnimationInfo {
        String name;
        Transition transition;
        TransitionValues values;
        public View view;
        WindowId windowId;

        AnimationInfo(View view, String name, Transition transition, WindowId windowId, TransitionValues values) {
            this.view = view;
            this.name = name;
            this.values = values;
            this.windowId = windowId;
            this.transition = transition;
        }
    }

    private static class ArrayListManager {
        private ArrayListManager() {
        }

        static <T> ArrayList<T> add(ArrayList<T> list, T item) {
            if (list == null) {
                list = new ArrayList();
            }
            if (!list.contains(item)) {
                list.add(item);
            }
            return list;
        }

        static <T> ArrayList<T> remove(ArrayList<T> list, T item) {
            if (list == null) {
                return list;
            }
            list.remove(item);
            if (list.isEmpty()) {
                return null;
            }
            return list;
        }
    }

    public static abstract class EpicenterCallback {
        public abstract Rect onGetEpicenter(Transition transition);
    }

    public interface TransitionListener {
        void onTransitionCancel(Transition transition);

        void onTransitionEnd(Transition transition);

        void onTransitionPause(Transition transition);

        void onTransitionResume(Transition transition);

        void onTransitionStart(Transition transition);
    }

    public abstract void captureEndValues(TransitionValues transitionValues);

    public abstract void captureStartValues(TransitionValues transitionValues);

    public Transition(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Transition);
        long duration = (long) a.getInt(1, -1);
        if (duration >= 0) {
            setDuration(duration);
        }
        long startDelay = (long) a.getInt(2, -1);
        if (startDelay > 0) {
            setStartDelay(startDelay);
        }
        int resID = a.getResourceId(0, 0);
        if (resID > 0) {
            setInterpolator(AnimationUtils.loadInterpolator(context, resID));
        }
        String matchOrder = a.getString(3);
        if (matchOrder != null) {
            setMatchOrder(parseMatchOrder(matchOrder));
        }
        a.recycle();
    }

    private static int[] parseMatchOrder(String matchOrderString) {
        StringTokenizer st = new StringTokenizer(matchOrderString, ",");
        int[] matches = new int[st.countTokens()];
        int index = 0;
        while (st.hasMoreTokens()) {
            String token = st.nextToken().trim();
            if (MATCH_ID_STR.equalsIgnoreCase(token)) {
                matches[index] = 3;
            } else if (MATCH_INSTANCE_STR.equalsIgnoreCase(token)) {
                matches[index] = 1;
            } else if ("name".equalsIgnoreCase(token)) {
                matches[index] = 2;
            } else if (MATCH_VIEW_NAME_STR.equalsIgnoreCase(token)) {
                matches[index] = 2;
            } else if (MATCH_ITEM_ID_STR.equalsIgnoreCase(token)) {
                matches[index] = 4;
            } else if (token.isEmpty()) {
                int[] smallerMatches = new int[(matches.length - 1)];
                System.arraycopy(matches, 0, smallerMatches, 0, index);
                matches = smallerMatches;
                index--;
            } else {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("Unknown match type in matchOrder: '");
                stringBuilder.append(token);
                stringBuilder.append("'");
                throw new InflateException(stringBuilder.toString());
            }
            index++;
        }
        return matches;
    }

    public Transition setDuration(long duration) {
        this.mDuration = duration;
        return this;
    }

    public long getDuration() {
        return this.mDuration;
    }

    public Transition setStartDelay(long startDelay) {
        this.mStartDelay = startDelay;
        return this;
    }

    public long getStartDelay() {
        return this.mStartDelay;
    }

    public Transition setInterpolator(TimeInterpolator interpolator) {
        this.mInterpolator = interpolator;
        return this;
    }

    public TimeInterpolator getInterpolator() {
        return this.mInterpolator;
    }

    public String[] getTransitionProperties() {
        return null;
    }

    public Animator createAnimator(ViewGroup sceneRoot, TransitionValues startValues, TransitionValues endValues) {
        return null;
    }

    public void setMatchOrder(int... matches) {
        if (matches == null || matches.length == 0) {
            this.mMatchOrder = DEFAULT_MATCH_ORDER;
            return;
        }
        int i = 0;
        while (i < matches.length) {
            if (!isValidMatch(matches[i])) {
                throw new IllegalArgumentException("matches contains invalid value");
            } else if (alreadyContains(matches, i)) {
                throw new IllegalArgumentException("matches contains a duplicate value");
            } else {
                i++;
            }
        }
        this.mMatchOrder = (int[]) matches.clone();
    }

    private static boolean isValidMatch(int match) {
        return match >= 1 && match <= 4;
    }

    private static boolean alreadyContains(int[] array, int searchIndex) {
        int value = array[searchIndex];
        for (int i = 0; i < searchIndex; i++) {
            if (array[i] == value) {
                return true;
            }
        }
        return false;
    }

    private void matchInstances(ArrayMap<View, TransitionValues> unmatchedStart, ArrayMap<View, TransitionValues> unmatchedEnd) {
        for (int i = unmatchedStart.size() - 1; i >= 0; i--) {
            View view = (View) unmatchedStart.keyAt(i);
            if (view != null && isValidTarget(view)) {
                TransitionValues end = (TransitionValues) unmatchedEnd.remove(view);
                if (!(end == null || end.view == null || !isValidTarget(end.view))) {
                    this.mStartValuesList.add((TransitionValues) unmatchedStart.removeAt(i));
                    this.mEndValuesList.add(end);
                }
            }
        }
    }

    private void matchItemIds(ArrayMap<View, TransitionValues> unmatchedStart, ArrayMap<View, TransitionValues> unmatchedEnd, LongSparseArray<View> startItemIds, LongSparseArray<View> endItemIds) {
        int numStartIds = startItemIds.size();
        for (int i = 0; i < numStartIds; i++) {
            View startView = (View) startItemIds.valueAt(i);
            if (startView != null && isValidTarget(startView)) {
                View endView = (View) endItemIds.get(startItemIds.keyAt(i));
                if (endView != null && isValidTarget(endView)) {
                    TransitionValues startValues = (TransitionValues) unmatchedStart.get(startView);
                    TransitionValues endValues = (TransitionValues) unmatchedEnd.get(endView);
                    if (!(startValues == null || endValues == null)) {
                        this.mStartValuesList.add(startValues);
                        this.mEndValuesList.add(endValues);
                        unmatchedStart.remove(startView);
                        unmatchedEnd.remove(endView);
                    }
                }
            }
        }
    }

    private void matchIds(ArrayMap<View, TransitionValues> unmatchedStart, ArrayMap<View, TransitionValues> unmatchedEnd, SparseArray<View> startIds, SparseArray<View> endIds) {
        int numStartIds = startIds.size();
        for (int i = 0; i < numStartIds; i++) {
            View startView = (View) startIds.valueAt(i);
            if (startView != null && isValidTarget(startView)) {
                View endView = (View) endIds.get(startIds.keyAt(i));
                if (endView != null && isValidTarget(endView)) {
                    TransitionValues startValues = (TransitionValues) unmatchedStart.get(startView);
                    TransitionValues endValues = (TransitionValues) unmatchedEnd.get(endView);
                    if (!(startValues == null || endValues == null)) {
                        this.mStartValuesList.add(startValues);
                        this.mEndValuesList.add(endValues);
                        unmatchedStart.remove(startView);
                        unmatchedEnd.remove(endView);
                    }
                }
            }
        }
    }

    private void matchNames(ArrayMap<View, TransitionValues> unmatchedStart, ArrayMap<View, TransitionValues> unmatchedEnd, ArrayMap<String, View> startNames, ArrayMap<String, View> endNames) {
        int numStartNames = startNames.size();
        for (int i = 0; i < numStartNames; i++) {
            View startView = (View) startNames.valueAt(i);
            if (startView != null && isValidTarget(startView)) {
                View endView = (View) endNames.get(startNames.keyAt(i));
                if (endView != null && isValidTarget(endView)) {
                    TransitionValues startValues = (TransitionValues) unmatchedStart.get(startView);
                    TransitionValues endValues = (TransitionValues) unmatchedEnd.get(endView);
                    if (!(startValues == null || endValues == null)) {
                        this.mStartValuesList.add(startValues);
                        this.mEndValuesList.add(endValues);
                        unmatchedStart.remove(startView);
                        unmatchedEnd.remove(endView);
                    }
                }
            }
        }
    }

    private void addUnmatched(ArrayMap<View, TransitionValues> unmatchedStart, ArrayMap<View, TransitionValues> unmatchedEnd) {
        int i = 0;
        for (int i2 = 0; i2 < unmatchedStart.size(); i2++) {
            TransitionValues start = (TransitionValues) unmatchedStart.valueAt(i2);
            if (isValidTarget(start.view)) {
                this.mStartValuesList.add(start);
                this.mEndValuesList.add(null);
            }
        }
        while (i < unmatchedEnd.size()) {
            TransitionValues end = (TransitionValues) unmatchedEnd.valueAt(i);
            if (isValidTarget(end.view)) {
                this.mEndValuesList.add(end);
                this.mStartValuesList.add(null);
            }
            i++;
        }
    }

    private void matchStartAndEnd(TransitionValuesMaps startValues, TransitionValuesMaps endValues) {
        ArrayMap<View, TransitionValues> unmatchedStart = new ArrayMap(startValues.viewValues);
        ArrayMap<View, TransitionValues> unmatchedEnd = new ArrayMap(endValues.viewValues);
        for (int i : this.mMatchOrder) {
            switch (i) {
                case 1:
                    matchInstances(unmatchedStart, unmatchedEnd);
                    break;
                case 2:
                    matchNames(unmatchedStart, unmatchedEnd, startValues.nameValues, endValues.nameValues);
                    break;
                case 3:
                    matchIds(unmatchedStart, unmatchedEnd, startValues.idValues, endValues.idValues);
                    break;
                case 4:
                    matchItemIds(unmatchedStart, unmatchedEnd, startValues.itemIdValues, endValues.itemIdValues);
                    break;
                default:
                    break;
            }
        }
        addUnmatched(unmatchedStart, unmatchedEnd);
    }

    protected void createAnimators(ViewGroup sceneRoot, TransitionValuesMaps startValues, TransitionValuesMaps endValues, ArrayList<TransitionValues> startValuesList, ArrayList<TransitionValues> endValuesList) {
        int startValuesListCount;
        ViewGroup viewGroup = sceneRoot;
        ArrayMap<Animator, AnimationInfo> runningAnimators = getRunningAnimators();
        int minAnimator = this.mAnimators.size();
        SparseLongArray startDelays = new SparseLongArray();
        int startValuesListCount2 = startValuesList.size();
        long minStartDelay = SubscriptionPlan.BYTES_UNLIMITED;
        int i = 0;
        while (true) {
            int i2 = i;
            if (i2 >= startValuesListCount2) {
                break;
            }
            int minAnimator2;
            int i3;
            TransitionValues start = (TransitionValues) startValuesList.get(i2);
            TransitionValues end = (TransitionValues) endValuesList.get(i2);
            if (!(start == null || start.targetedTransitions.contains(this))) {
                start = null;
            }
            TransitionValues start2 = start;
            if (!(end == null || end.targetedTransitions.contains(this))) {
                end = null;
            }
            TransitionValues end2 = end;
            if (start2 == null && end2 == null) {
                minAnimator2 = minAnimator;
                startValuesListCount = startValuesListCount2;
                i3 = i2;
            } else {
                boolean z = start2 == null || end2 == null || isTransitionRequired(start2, end2);
                if (z) {
                    Animator animator = createAnimator(viewGroup, start2, end2);
                    if (animator != null) {
                        View view;
                        TransitionValues infoValues = null;
                        if (end2 != null) {
                            Animator animator2;
                            View view2 = end2.view;
                            String[] properties = getTransitionProperties();
                            if (view2 == null || properties == null) {
                                animator2 = animator;
                                view = view2;
                                minAnimator2 = minAnimator;
                                startValuesListCount = startValuesListCount2;
                                i3 = i2;
                            } else {
                                animator2 = animator;
                                if (properties.length > null) {
                                    animator = new TransitionValues();
                                    animator.view = view2;
                                    minAnimator2 = minAnimator;
                                    startValuesListCount = startValuesListCount2;
                                    TransitionValues newValues = (TransitionValues) endValues.viewValues.get(view2);
                                    if (newValues != null) {
                                        int j = 0;
                                        while (true) {
                                            int j2 = j;
                                            if (j2 >= properties.length) {
                                                break;
                                            }
                                            i3 = i2;
                                            TransitionValues newValues2 = newValues;
                                            animator.values.put(properties[j2], newValues.values.get(properties[j2]));
                                            j = j2 + 1;
                                            i2 = i3;
                                            newValues = newValues2;
                                            minAnimator = endValues;
                                            ArrayList<TransitionValues> arrayList = startValuesList;
                                            ArrayList<TransitionValues> arrayList2 = endValuesList;
                                        }
                                    }
                                    i3 = i2;
                                    minAnimator = runningAnimators.size();
                                    startValuesListCount2 = 0;
                                    while (startValuesListCount2 < minAnimator) {
                                        AnimationInfo info = (AnimationInfo) runningAnimators.get((Animator) runningAnimators.keyAt(startValuesListCount2));
                                        if (info.values == null || info.view != view2) {
                                            view = view2;
                                        } else {
                                            if (info.name == null && getName() == null) {
                                                view = view2;
                                            } else {
                                                view = view2;
                                                if (info.name.equals(getName()) == null) {
                                                    continue;
                                                }
                                            }
                                            if (info.values.equals(animator) != null) {
                                                infoValues = animator;
                                                animator = null;
                                                break;
                                            }
                                        }
                                        startValuesListCount2++;
                                        view2 = view;
                                    }
                                    view = view2;
                                    infoValues = animator;
                                    animator = animator2;
                                    minAnimator = animator;
                                } else {
                                    view = view2;
                                    minAnimator2 = minAnimator;
                                    startValuesListCount = startValuesListCount2;
                                    i3 = i2;
                                }
                            }
                            animator = animator2;
                            minAnimator = animator;
                        } else {
                            minAnimator2 = minAnimator;
                            startValuesListCount = startValuesListCount2;
                            i3 = i2;
                            view = start2 != null ? start2.view : null;
                            minAnimator = animator;
                        }
                        if (minAnimator != 0) {
                            if (this.mPropagation != null) {
                                long delay = this.mPropagation.getStartDelay(viewGroup, this, start2, end2);
                                startDelays.put(this.mAnimators.size(), delay);
                                minStartDelay = Math.min(delay, minStartDelay);
                            }
                            startValuesListCount2 = minStartDelay;
                            runningAnimators.put(minAnimator, new AnimationInfo(view, getName(), this, sceneRoot.getWindowId(), infoValues));
                            this.mAnimators.add(minAnimator);
                            minStartDelay = startValuesListCount2;
                        }
                    }
                }
                minAnimator2 = minAnimator;
                startValuesListCount = startValuesListCount2;
                i3 = i2;
            }
            i = i3 + 1;
            minAnimator = minAnimator2;
            startValuesListCount2 = startValuesListCount;
        }
        startValuesListCount = startValuesListCount2;
        if (startDelays.size() != 0) {
            int i4 = 0;
            while (true) {
                i = i4;
                if (i < startDelays.size()) {
                    Animator animator3 = (Animator) this.mAnimators.get(startDelays.keyAt(i));
                    animator3.setStartDelay((startDelays.valueAt(i) - minStartDelay) + animator3.getStartDelay());
                    i4 = i + 1;
                } else {
                    return;
                }
            }
        }
    }

    /* JADX WARNING: Missing block: B:42:0x0089, code skipped:
            return true;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean isValidTarget(View target) {
        if (target == null) {
            return false;
        }
        int targetId = target.getId();
        if (this.mTargetIdExcludes != null && this.mTargetIdExcludes.contains(Integer.valueOf(targetId))) {
            return false;
        }
        if (this.mTargetExcludes != null && this.mTargetExcludes.contains(target)) {
            return false;
        }
        int numTypes;
        if (!(this.mTargetTypeExcludes == null || target == null)) {
            numTypes = this.mTargetTypeExcludes.size();
            for (int i = 0; i < numTypes; i++) {
                if (((Class) this.mTargetTypeExcludes.get(i)).isInstance(target)) {
                    return false;
                }
            }
        }
        if (this.mTargetNameExcludes != null && target != null && target.getTransitionName() != null && this.mTargetNameExcludes.contains(target.getTransitionName())) {
            return false;
        }
        if ((this.mTargetIds.size() == 0 && this.mTargets.size() == 0 && ((this.mTargetTypes == null || this.mTargetTypes.isEmpty()) && (this.mTargetNames == null || this.mTargetNames.isEmpty()))) || this.mTargetIds.contains(Integer.valueOf(targetId)) || this.mTargets.contains(target)) {
            return true;
        }
        if (this.mTargetNames != null && this.mTargetNames.contains(target.getTransitionName())) {
            return true;
        }
        if (this.mTargetTypes != null) {
            for (numTypes = 0; numTypes < this.mTargetTypes.size(); numTypes++) {
                if (((Class) this.mTargetTypes.get(numTypes)).isInstance(target)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ArrayMap<Animator, AnimationInfo> getRunningAnimators() {
        ArrayMap<Animator, AnimationInfo> runningAnimators = (ArrayMap) sRunningAnimators.get();
        if (runningAnimators != null) {
            return runningAnimators;
        }
        runningAnimators = new ArrayMap();
        sRunningAnimators.set(runningAnimators);
        return runningAnimators;
    }

    protected void runAnimators() {
        start();
        ArrayMap<Animator, AnimationInfo> runningAnimators = getRunningAnimators();
        Iterator it = this.mAnimators.iterator();
        while (it.hasNext()) {
            Animator anim = (Animator) it.next();
            if (runningAnimators.containsKey(anim)) {
                start();
                runAnimator(anim, runningAnimators);
            }
        }
        this.mAnimators.clear();
        end();
    }

    private void runAnimator(Animator animator, final ArrayMap<Animator, AnimationInfo> runningAnimators) {
        if (animator != null) {
            animator.addListener(new AnimatorListenerAdapter() {
                public void onAnimationStart(Animator animation) {
                    Transition.this.mCurrentAnimators.add(animation);
                }

                public void onAnimationEnd(Animator animation) {
                    runningAnimators.remove(animation);
                    Transition.this.mCurrentAnimators.remove(animation);
                }
            });
            animate(animator);
        }
    }

    public Transition addTarget(int targetId) {
        if (targetId > 0) {
            this.mTargetIds.add(Integer.valueOf(targetId));
        }
        return this;
    }

    public Transition addTarget(String targetName) {
        if (targetName != null) {
            if (this.mTargetNames == null) {
                this.mTargetNames = new ArrayList();
            }
            this.mTargetNames.add(targetName);
        }
        return this;
    }

    public Transition addTarget(Class targetType) {
        if (targetType != null) {
            if (this.mTargetTypes == null) {
                this.mTargetTypes = new ArrayList();
            }
            this.mTargetTypes.add(targetType);
        }
        return this;
    }

    public Transition removeTarget(int targetId) {
        if (targetId > 0) {
            this.mTargetIds.remove(Integer.valueOf(targetId));
        }
        return this;
    }

    public Transition removeTarget(String targetName) {
        if (!(targetName == null || this.mTargetNames == null)) {
            this.mTargetNames.remove(targetName);
        }
        return this;
    }

    public Transition excludeTarget(int targetId, boolean exclude) {
        if (targetId >= 0) {
            this.mTargetIdExcludes = excludeObject(this.mTargetIdExcludes, Integer.valueOf(targetId), exclude);
        }
        return this;
    }

    public Transition excludeTarget(String targetName, boolean exclude) {
        this.mTargetNameExcludes = excludeObject(this.mTargetNameExcludes, targetName, exclude);
        return this;
    }

    public Transition excludeChildren(int targetId, boolean exclude) {
        if (targetId >= 0) {
            this.mTargetIdChildExcludes = excludeObject(this.mTargetIdChildExcludes, Integer.valueOf(targetId), exclude);
        }
        return this;
    }

    public Transition excludeTarget(View target, boolean exclude) {
        this.mTargetExcludes = excludeObject(this.mTargetExcludes, target, exclude);
        return this;
    }

    public Transition excludeChildren(View target, boolean exclude) {
        this.mTargetChildExcludes = excludeObject(this.mTargetChildExcludes, target, exclude);
        return this;
    }

    private static <T> ArrayList<T> excludeObject(ArrayList<T> list, T target, boolean exclude) {
        if (target == null) {
            return list;
        }
        if (exclude) {
            return ArrayListManager.add(list, target);
        }
        return ArrayListManager.remove(list, target);
    }

    public Transition excludeTarget(Class type, boolean exclude) {
        this.mTargetTypeExcludes = excludeObject(this.mTargetTypeExcludes, type, exclude);
        return this;
    }

    public Transition excludeChildren(Class type, boolean exclude) {
        this.mTargetTypeChildExcludes = excludeObject(this.mTargetTypeChildExcludes, type, exclude);
        return this;
    }

    public Transition addTarget(View target) {
        this.mTargets.add(target);
        return this;
    }

    public Transition removeTarget(View target) {
        if (target != null) {
            this.mTargets.remove(target);
        }
        return this;
    }

    public Transition removeTarget(Class target) {
        if (target != null) {
            this.mTargetTypes.remove(target);
        }
        return this;
    }

    public List<Integer> getTargetIds() {
        return this.mTargetIds;
    }

    public List<View> getTargets() {
        return this.mTargets;
    }

    public List<String> getTargetNames() {
        return this.mTargetNames;
    }

    public List<String> getTargetViewNames() {
        return this.mTargetNames;
    }

    public List<Class> getTargetTypes() {
        return this.mTargetTypes;
    }

    void captureValues(ViewGroup sceneRoot, boolean start) {
        int i;
        View view;
        clearValues(start);
        int i2 = 0;
        if ((this.mTargetIds.size() > 0 || this.mTargets.size() > 0) && ((this.mTargetNames == null || this.mTargetNames.isEmpty()) && (this.mTargetTypes == null || this.mTargetTypes.isEmpty()))) {
            for (i = 0; i < this.mTargetIds.size(); i++) {
                view = sceneRoot.findViewById(((Integer) this.mTargetIds.get(i)).intValue());
                if (view != null) {
                    TransitionValues values = new TransitionValues();
                    values.view = view;
                    if (start) {
                        captureStartValues(values);
                    } else {
                        captureEndValues(values);
                    }
                    values.targetedTransitions.add(this);
                    capturePropagationValues(values);
                    if (start) {
                        addViewValues(this.mStartValues, view, values);
                    } else {
                        addViewValues(this.mEndValues, view, values);
                    }
                }
            }
            for (i = 0; i < this.mTargets.size(); i++) {
                View view2 = (View) this.mTargets.get(i);
                TransitionValues values2 = new TransitionValues();
                values2.view = view2;
                if (start) {
                    captureStartValues(values2);
                } else {
                    captureEndValues(values2);
                }
                values2.targetedTransitions.add(this);
                capturePropagationValues(values2);
                if (start) {
                    addViewValues(this.mStartValues, view2, values2);
                } else {
                    addViewValues(this.mEndValues, view2, values2);
                }
            }
        } else {
            captureHierarchy(sceneRoot, start);
        }
        if (!start && this.mNameOverrides != null) {
            i = this.mNameOverrides.size();
            ArrayList<View> overriddenViews = new ArrayList(i);
            for (int i3 = 0; i3 < i; i3++) {
                overriddenViews.add((View) this.mStartValues.nameValues.remove((String) this.mNameOverrides.keyAt(i3)));
            }
            while (i2 < i) {
                view = (View) overriddenViews.get(i2);
                if (view != null) {
                    this.mStartValues.nameValues.put((String) this.mNameOverrides.valueAt(i2), view);
                }
                i2++;
            }
        }
    }

    static void addViewValues(TransitionValuesMaps transitionValuesMaps, View view, TransitionValues transitionValues) {
        transitionValuesMaps.viewValues.put(view, transitionValues);
        int id = view.getId();
        if (id >= 0) {
            if (transitionValuesMaps.idValues.indexOfKey(id) >= 0) {
                transitionValuesMaps.idValues.put(id, null);
            } else {
                transitionValuesMaps.idValues.put(id, view);
            }
        }
        String name = view.getTransitionName();
        if (name != null) {
            if (transitionValuesMaps.nameValues.containsKey(name)) {
                transitionValuesMaps.nameValues.put(name, null);
            } else {
                transitionValuesMaps.nameValues.put(name, view);
            }
        }
        if (view.getParent() instanceof ListView) {
            ListView listview = (ListView) view.getParent();
            if (listview.getAdapter().hasStableIds()) {
                long itemId = listview.getItemIdAtPosition(listview.getPositionForView(view));
                if (transitionValuesMaps.itemIdValues.indexOfKey(itemId) >= 0) {
                    View alreadyMatched = (View) transitionValuesMaps.itemIdValues.get(itemId);
                    if (alreadyMatched != null) {
                        alreadyMatched.setHasTransientState(false);
                        transitionValuesMaps.itemIdValues.put(itemId, null);
                        return;
                    }
                    return;
                }
                view.setHasTransientState(true);
                transitionValuesMaps.itemIdValues.put(itemId, view);
            }
        }
    }

    void clearValues(boolean start) {
        if (start) {
            this.mStartValues.viewValues.clear();
            this.mStartValues.idValues.clear();
            this.mStartValues.itemIdValues.clear();
            this.mStartValues.nameValues.clear();
            this.mStartValuesList = null;
            return;
        }
        this.mEndValues.viewValues.clear();
        this.mEndValues.idValues.clear();
        this.mEndValues.itemIdValues.clear();
        this.mEndValues.nameValues.clear();
        this.mEndValuesList = null;
    }

    private void captureHierarchy(View view, boolean start) {
        if (view != null) {
            int id = view.getId();
            if (this.mTargetIdExcludes != null && this.mTargetIdExcludes.contains(Integer.valueOf(id))) {
                return;
            }
            if (this.mTargetExcludes == null || !this.mTargetExcludes.contains(view)) {
                int numTypes;
                int i;
                int i2 = 0;
                if (!(this.mTargetTypeExcludes == null || view == null)) {
                    numTypes = this.mTargetTypeExcludes.size();
                    i = 0;
                    while (i < numTypes) {
                        if (!((Class) this.mTargetTypeExcludes.get(i)).isInstance(view)) {
                            i++;
                        } else {
                            return;
                        }
                    }
                }
                if (view.getParent() instanceof ViewGroup) {
                    TransitionValues values = new TransitionValues();
                    values.view = view;
                    if (start) {
                        captureStartValues(values);
                    } else {
                        captureEndValues(values);
                    }
                    values.targetedTransitions.add(this);
                    capturePropagationValues(values);
                    if (start) {
                        addViewValues(this.mStartValues, view, values);
                    } else {
                        addViewValues(this.mEndValues, view, values);
                    }
                }
                if ((view instanceof ViewGroup) && (this.mTargetIdChildExcludes == null || !this.mTargetIdChildExcludes.contains(Integer.valueOf(id)))) {
                    if (this.mTargetChildExcludes == null || !this.mTargetChildExcludes.contains(view)) {
                        if (this.mTargetTypeChildExcludes != null) {
                            numTypes = this.mTargetTypeChildExcludes.size();
                            i = 0;
                            while (i < numTypes) {
                                if (!((Class) this.mTargetTypeChildExcludes.get(i)).isInstance(view)) {
                                    i++;
                                } else {
                                    return;
                                }
                            }
                        }
                        ViewGroup parent = (ViewGroup) view;
                        while (i2 < parent.getChildCount()) {
                            captureHierarchy(parent.getChildAt(i2), start);
                            i2++;
                        }
                    }
                }
            }
        }
    }

    public TransitionValues getTransitionValues(View view, boolean start) {
        if (this.mParent != null) {
            return this.mParent.getTransitionValues(view, start);
        }
        return (TransitionValues) (start ? this.mStartValues : this.mEndValues).viewValues.get(view);
    }

    TransitionValues getMatchedTransitionValues(View view, boolean viewInStart) {
        if (this.mParent != null) {
            return this.mParent.getMatchedTransitionValues(view, viewInStart);
        }
        ArrayList<TransitionValues> lookIn = viewInStart ? this.mStartValuesList : this.mEndValuesList;
        if (lookIn == null) {
            return null;
        }
        int count = lookIn.size();
        int index = -1;
        for (int i = 0; i < count; i++) {
            TransitionValues values = (TransitionValues) lookIn.get(i);
            if (values == null) {
                return null;
            }
            if (values.view == view) {
                index = i;
                break;
            }
        }
        TransitionValues values2 = null;
        if (index >= 0) {
            values2 = (TransitionValues) (viewInStart ? this.mEndValuesList : this.mStartValuesList).get(index);
        }
        return values2;
    }

    public void pause(View sceneRoot) {
        if (!this.mEnded) {
            int i;
            ArrayMap<Animator, AnimationInfo> runningAnimators = getRunningAnimators();
            int numOldAnims = runningAnimators.size();
            if (sceneRoot != null) {
                WindowId windowId = sceneRoot.getWindowId();
                for (i = numOldAnims - 1; i >= 0; i--) {
                    AnimationInfo info = (AnimationInfo) runningAnimators.valueAt(i);
                    if (!(info.view == null || windowId == null || !windowId.equals(info.windowId))) {
                        ((Animator) runningAnimators.keyAt(i)).pause();
                    }
                }
            }
            if (this.mListeners != null && this.mListeners.size() > 0) {
                ArrayList<TransitionListener> tmpListeners = (ArrayList) this.mListeners.clone();
                i = tmpListeners.size();
                for (int i2 = 0; i2 < i; i2++) {
                    ((TransitionListener) tmpListeners.get(i2)).onTransitionPause(this);
                }
            }
            this.mPaused = true;
        }
    }

    public void resume(View sceneRoot) {
        if (this.mPaused) {
            if (!this.mEnded) {
                ArrayMap<Animator, AnimationInfo> runningAnimators = getRunningAnimators();
                int numOldAnims = runningAnimators.size();
                WindowId windowId = sceneRoot.getWindowId();
                for (int i = numOldAnims - 1; i >= 0; i--) {
                    AnimationInfo info = (AnimationInfo) runningAnimators.valueAt(i);
                    if (!(info.view == null || windowId == null || !windowId.equals(info.windowId))) {
                        ((Animator) runningAnimators.keyAt(i)).resume();
                    }
                }
                if (this.mListeners != null && this.mListeners.size() > 0) {
                    ArrayList<TransitionListener> tmpListeners = (ArrayList) this.mListeners.clone();
                    int numListeners = tmpListeners.size();
                    for (int i2 = 0; i2 < numListeners; i2++) {
                        ((TransitionListener) tmpListeners.get(i2)).onTransitionResume(this);
                    }
                }
            }
            this.mPaused = false;
        }
    }

    void playTransition(ViewGroup sceneRoot) {
        this.mStartValuesList = new ArrayList();
        this.mEndValuesList = new ArrayList();
        matchStartAndEnd(this.mStartValues, this.mEndValues);
        ArrayMap<Animator, AnimationInfo> runningAnimators = getRunningAnimators();
        int numOldAnims = runningAnimators.size();
        WindowId windowId = sceneRoot.getWindowId();
        for (int i = numOldAnims - 1; i >= 0; i--) {
            Animator anim = (Animator) runningAnimators.keyAt(i);
            if (anim != null) {
                AnimationInfo oldInfo = (AnimationInfo) runningAnimators.get(anim);
                if (!(oldInfo == null || oldInfo.view == null || oldInfo.windowId != windowId)) {
                    TransitionValues oldValues = oldInfo.values;
                    View oldView = oldInfo.view;
                    boolean cancel = true;
                    TransitionValues startValues = getTransitionValues(oldView, true);
                    TransitionValues endValues = getMatchedTransitionValues(oldView, true);
                    if (startValues == null && endValues == null) {
                        endValues = (TransitionValues) this.mEndValues.viewValues.get(oldView);
                    }
                    if ((startValues == null && endValues == null) || !oldInfo.transition.isTransitionRequired(oldValues, endValues)) {
                        cancel = false;
                    }
                    if (cancel) {
                        if (anim.isRunning() || anim.isStarted()) {
                            anim.cancel();
                        } else {
                            runningAnimators.remove(anim);
                        }
                    }
                }
            }
        }
        createAnimators(sceneRoot, this.mStartValues, this.mEndValues, this.mStartValuesList, this.mEndValuesList);
        runAnimators();
    }

    public boolean isTransitionRequired(TransitionValues startValues, TransitionValues endValues) {
        if (startValues == null || endValues == null) {
            return false;
        }
        String[] properties = getTransitionProperties();
        if (properties != null) {
            for (String isValueChanged : properties) {
                if (isValueChanged(startValues, endValues, isValueChanged)) {
                    return true;
                }
            }
            return false;
        }
        for (String key : startValues.values.keySet()) {
            if (isValueChanged(startValues, endValues, key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValueChanged(TransitionValues oldValues, TransitionValues newValues, String key) {
        if (oldValues.values.containsKey(key) != newValues.values.containsKey(key)) {
            return false;
        }
        boolean changed;
        Object oldValue = oldValues.values.get(key);
        Object newValue = newValues.values.get(key);
        if (oldValue == null && newValue == null) {
            changed = false;
        } else if (oldValue == null || newValue == null) {
            changed = true;
        } else {
            changed = oldValue.equals(newValue) ^ 1;
        }
        return changed;
    }

    protected void animate(Animator animator) {
        if (animator == null) {
            end();
            return;
        }
        if (getDuration() >= 0) {
            animator.setDuration(getDuration());
        }
        if (getStartDelay() >= 0) {
            animator.setStartDelay(getStartDelay() + animator.getStartDelay());
        }
        if (getInterpolator() != null) {
            animator.setInterpolator(getInterpolator());
        }
        animator.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                Transition.this.end();
                animation.removeListener(this);
            }
        });
        animator.start();
    }

    protected void start() {
        if (this.mNumInstances == 0) {
            if (this.mListeners != null && this.mListeners.size() > 0) {
                ArrayList<TransitionListener> tmpListeners = (ArrayList) this.mListeners.clone();
                int numListeners = tmpListeners.size();
                for (int i = 0; i < numListeners; i++) {
                    ((TransitionListener) tmpListeners.get(i)).onTransitionStart(this);
                }
            }
            this.mEnded = false;
        }
        this.mNumInstances++;
    }

    protected void end() {
        this.mNumInstances--;
        if (this.mNumInstances == 0) {
            int i;
            View view;
            if (this.mListeners != null && this.mListeners.size() > 0) {
                ArrayList<TransitionListener> tmpListeners = (ArrayList) this.mListeners.clone();
                int numListeners = tmpListeners.size();
                for (int i2 = 0; i2 < numListeners; i2++) {
                    ((TransitionListener) tmpListeners.get(i2)).onTransitionEnd(this);
                }
            }
            for (i = 0; i < this.mStartValues.itemIdValues.size(); i++) {
                view = (View) this.mStartValues.itemIdValues.valueAt(i);
                if (view != null) {
                    view.setHasTransientState(false);
                }
            }
            for (i = 0; i < this.mEndValues.itemIdValues.size(); i++) {
                view = (View) this.mEndValues.itemIdValues.valueAt(i);
                if (view != null) {
                    view.setHasTransientState(false);
                }
            }
            this.mEnded = true;
        }
    }

    void forceToEnd(ViewGroup sceneRoot) {
        ArrayMap<Animator, AnimationInfo> runningAnimators = getRunningAnimators();
        int numOldAnims = runningAnimators.size();
        if (sceneRoot != null) {
            WindowId windowId = sceneRoot.getWindowId();
            for (int i = numOldAnims - 1; i >= 0; i--) {
                AnimationInfo info = (AnimationInfo) runningAnimators.valueAt(i);
                if (!(info.view == null || windowId == null || !windowId.equals(info.windowId))) {
                    ((Animator) runningAnimators.keyAt(i)).end();
                }
            }
        }
    }

    protected void cancel() {
        for (int i = this.mCurrentAnimators.size() - 1; i >= 0; i--) {
            ((Animator) this.mCurrentAnimators.get(i)).cancel();
        }
        if (this.mListeners != null && this.mListeners.size() > 0) {
            ArrayList<TransitionListener> tmpListeners = (ArrayList) this.mListeners.clone();
            int numListeners = tmpListeners.size();
            for (int i2 = 0; i2 < numListeners; i2++) {
                ((TransitionListener) tmpListeners.get(i2)).onTransitionCancel(this);
            }
        }
    }

    public Transition addListener(TransitionListener listener) {
        if (this.mListeners == null) {
            this.mListeners = new ArrayList();
        }
        this.mListeners.add(listener);
        return this;
    }

    public Transition removeListener(TransitionListener listener) {
        if (this.mListeners == null) {
            return this;
        }
        this.mListeners.remove(listener);
        if (this.mListeners.size() == 0) {
            this.mListeners = null;
        }
        return this;
    }

    public void setEpicenterCallback(EpicenterCallback epicenterCallback) {
        this.mEpicenterCallback = epicenterCallback;
    }

    public EpicenterCallback getEpicenterCallback() {
        return this.mEpicenterCallback;
    }

    public Rect getEpicenter() {
        if (this.mEpicenterCallback == null) {
            return null;
        }
        return this.mEpicenterCallback.onGetEpicenter(this);
    }

    public void setPathMotion(PathMotion pathMotion) {
        if (pathMotion == null) {
            this.mPathMotion = STRAIGHT_PATH_MOTION;
        } else {
            this.mPathMotion = pathMotion;
        }
    }

    public PathMotion getPathMotion() {
        return this.mPathMotion;
    }

    public void setPropagation(TransitionPropagation transitionPropagation) {
        this.mPropagation = transitionPropagation;
    }

    public TransitionPropagation getPropagation() {
        return this.mPropagation;
    }

    void capturePropagationValues(TransitionValues transitionValues) {
        if (!(this.mPropagation == null || transitionValues.values.isEmpty())) {
            String[] propertyNames = this.mPropagation.getPropagationProperties();
            if (propertyNames != null) {
                boolean containsAll = true;
                for (Object containsKey : propertyNames) {
                    if (!transitionValues.values.containsKey(containsKey)) {
                        containsAll = false;
                        break;
                    }
                }
                if (!containsAll) {
                    this.mPropagation.captureValues(transitionValues);
                }
            }
        }
    }

    Transition setSceneRoot(ViewGroup sceneRoot) {
        this.mSceneRoot = sceneRoot;
        return this;
    }

    void setCanRemoveViews(boolean canRemoveViews) {
        this.mCanRemoveViews = canRemoveViews;
    }

    public boolean canRemoveViews() {
        return this.mCanRemoveViews;
    }

    public void setNameOverrides(ArrayMap<String, String> overrides) {
        this.mNameOverrides = overrides;
    }

    public ArrayMap<String, String> getNameOverrides() {
        return this.mNameOverrides;
    }

    public String toString() {
        return toString("");
    }

    public Transition clone() {
        Transition clone = null;
        try {
            clone = (Transition) super.clone();
            clone.mAnimators = new ArrayList();
            clone.mStartValues = new TransitionValuesMaps();
            clone.mEndValues = new TransitionValuesMaps();
            clone.mStartValuesList = null;
            clone.mEndValuesList = null;
            return clone;
        } catch (CloneNotSupportedException e) {
            return clone;
        }
    }

    public String getName() {
        return this.mName;
    }

    String toString(String indent) {
        StringBuilder stringBuilder;
        String result = new StringBuilder();
        result.append(indent);
        result.append(getClass().getSimpleName());
        result.append("@");
        result.append(Integer.toHexString(hashCode()));
        result.append(": ");
        result = result.toString();
        if (this.mDuration != -1) {
            stringBuilder = new StringBuilder();
            stringBuilder.append(result);
            stringBuilder.append("dur(");
            stringBuilder.append(this.mDuration);
            stringBuilder.append(") ");
            result = stringBuilder.toString();
        }
        if (this.mStartDelay != -1) {
            stringBuilder = new StringBuilder();
            stringBuilder.append(result);
            stringBuilder.append("dly(");
            stringBuilder.append(this.mStartDelay);
            stringBuilder.append(") ");
            result = stringBuilder.toString();
        }
        if (this.mInterpolator != null) {
            stringBuilder = new StringBuilder();
            stringBuilder.append(result);
            stringBuilder.append("interp(");
            stringBuilder.append(this.mInterpolator);
            stringBuilder.append(") ");
            result = stringBuilder.toString();
        }
        if (this.mTargetIds.size() <= 0 && this.mTargets.size() <= 0) {
            return result;
        }
        stringBuilder = new StringBuilder();
        stringBuilder.append(result);
        stringBuilder.append("tgts(");
        result = stringBuilder.toString();
        int i = 0;
        if (this.mTargetIds.size() > 0) {
            String result2 = result;
            for (int i2 = 0; i2 < this.mTargetIds.size(); i2++) {
                StringBuilder stringBuilder2;
                if (i2 > 0) {
                    stringBuilder2 = new StringBuilder();
                    stringBuilder2.append(result2);
                    stringBuilder2.append(", ");
                    result2 = stringBuilder2.toString();
                }
                stringBuilder2 = new StringBuilder();
                stringBuilder2.append(result2);
                stringBuilder2.append(this.mTargetIds.get(i2));
                result2 = stringBuilder2.toString();
            }
            result = result2;
        }
        if (this.mTargets.size() > 0) {
            while (true) {
                int i3 = i;
                if (i3 >= this.mTargets.size()) {
                    break;
                }
                StringBuilder stringBuilder3;
                if (i3 > 0) {
                    stringBuilder3 = new StringBuilder();
                    stringBuilder3.append(result);
                    stringBuilder3.append(", ");
                    result = stringBuilder3.toString();
                }
                stringBuilder3 = new StringBuilder();
                stringBuilder3.append(result);
                stringBuilder3.append(this.mTargets.get(i3));
                result = stringBuilder3.toString();
                i = i3 + 1;
            }
        }
        stringBuilder = new StringBuilder();
        stringBuilder.append(result);
        stringBuilder.append(")");
        return stringBuilder.toString();
    }
}
