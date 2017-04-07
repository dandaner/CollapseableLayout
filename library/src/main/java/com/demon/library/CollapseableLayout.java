package com.demon.library;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.UiThread;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.VelocityTrackerCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ListView;

/**
 * 可折叠的根布局:
 * 需要在XML文件中通过以下标记来指定相应元素:
 * app:collapse_header 标识header
 * app:collapse_content 标识content
 * app:collapse_scroll 标识真正可以滑动的View,目前支持的控件包括:Listview, Recyclerview, NestedScrollView
 * app:collapse_shrink_height 标识折叠到顶端的距离
 * <p>
 * author: demon.zhang
 * time  : 17/1/8 下午5:15
 */
@UiThread
public class CollapseableLayout extends LinearLayout {

    private static final int INVALID_VALUE = 0;
    private static final int INVALID_POINTER = -1;

    private int mHeaderId, mContentId, mScrollViewId;
    private int mCollapseHeight;

    private View mHeaderView;
    private View mContentView;
    /**
     * 真正可以滑动的孩子,通过他的行为来判断是否可以继续下滑
     */
    private View mRealScrollAbleView;

    private boolean mIsBeingDragged;

    private int mTouchSlop = -1;
    private int mLastMotionY;
    private int mActivePointerId = INVALID_POINTER;

    private int mCurOffset;

    /**
     * 是否可以折叠
     */
    private boolean mCollapseEnable = true;
    /**
     * 是否自动伸缩
     */
    private boolean mFlexible = true;

    private ValueAnimator mOffsetAnimator;
    private boolean mIsAnimRunning;

    private VelocityTracker mVelocityTracker;

    public CollapseableLayout(Context context) {
        this(context, null);
    }

    public CollapseableLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CollapseableLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);

        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.CollapseableLayout);
        mHeaderId = arr.getResourceId(R.styleable.CollapseableLayout_collapse_header, INVALID_VALUE);
        mContentId = arr.getResourceId(R.styleable.CollapseableLayout_collapse_content, INVALID_VALUE);
        mScrollViewId = arr.getResourceId(R.styleable.CollapseableLayout_collapse_scroll, INVALID_VALUE);
        mCollapseHeight = arr.getDimensionPixelOffset(R.styleable.CollapseableLayout_collapse_shrink_height, INVALID_VALUE);
        arr.recycle();

        initOffsetAnim();
    }

    private int mLastAnimateValue;

    private void initOffsetAnim() {
        mOffsetAnimator = new ValueAnimator();
        mOffsetAnimator.setInterpolator(new DecelerateInterpolator());
        mOffsetAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int value = (int) animation.getAnimatedValue();
                scroll(value - mLastAnimateValue);
                mLastAnimateValue = value;
            }
        });
        mOffsetAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mIsAnimRunning = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mIsAnimRunning = false;
            }
        });
        mOffsetAnimator.setDuration(200);
        mOffsetAnimator.setIntValues();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!mCollapseEnable) {
            return false;
        }
        if (mIsAnimRunning) {
            // 父容器在执行动画过程中,拦截触摸事件,禁止孩子元素乱动,导致界面元素混乱
            return true;
        }

        if (mTouchSlop < 0) {
            mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        }

        switch (MotionEventCompat.getActionMasked(ev)) {
            case MotionEvent.ACTION_DOWN: {
                mIsBeingDragged = false;
                mLastMotionY = (int) ev.getY();
                mActivePointerId = ev.getPointerId(0);
                ensureVelocityTracker();
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                final int activePointerId = mActivePointerId;
                if (activePointerId == INVALID_POINTER) {
                    break;
                }
                final int pointerIndex = ev.findPointerIndex(activePointerId);
                if (pointerIndex == -1) {
                    break;
                }

                final int y = (int) ev.getY(pointerIndex);
                final int yDiff = y - mLastMotionY;

                if (Math.abs(yDiff) > mTouchSlop) {
                    // 上滑,滑到顶端以后,事件交给孩子,自己不在处理
                    if (yDiff < 0 && mContentView.getTop() <= mCollapseHeight) {
                        return false;
                    }
                    // 下滑的时候,孩子只要能下滑,先让孩子下滑
                    if (yDiff > 0 && canChildScrollDown()) {
                        return false;
                    }

                    mIsBeingDragged = true;
                    mLastMotionY = y;
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                break;
            }
        }

        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(ev);
        }

        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mCollapseEnable) {
            return false;
        }
        if (mTouchSlop < 0) {
            mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        }
        switch (MotionEventCompat.getActionMasked(ev)) {
            case MotionEvent.ACTION_DOWN: {
                mLastMotionY = (int) ev.getY();
                mActivePointerId = ev.getPointerId(0);
                ensureVelocityTracker();
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                if (activePointerIndex == -1) {
                    return false;
                }

                final int y = (int) ev.getY(activePointerIndex);
                int dy = y - mLastMotionY;

                mLastMotionY = y;
                scroll(dy);
                break;
            }

            case MotionEvent.ACTION_UP:
                if (mFlexible && mVelocityTracker != null) {
                    mVelocityTracker.addMovement(ev);
                    mVelocityTracker.computeCurrentVelocity(1000);

                    fling(VelocityTrackerCompat.getYVelocity(mVelocityTracker,
                            mActivePointerId));
                }
            case MotionEvent.ACTION_CANCEL:
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;

                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                break;
        }

        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(ev);
        }

        return true;
    }

    private void ensureVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    private void animateOffsetTo(float factor) {
        if (mOffsetAnimator != null && mOffsetAnimator.isRunning()) {
            mOffsetAnimator.cancel();
        }
        if (Math.abs(mHeaderView.getTop()) < (mHeaderView.getHeight() - mCollapseHeight) * factor) {
            animateOffsetToEnd();
        } else {
            animateOffsetToStart();
        }
    }

    private void animateOffsetToStart() {
        mLastAnimateValue = mHeaderView.getHeight() - mCollapseHeight - Math.abs(mHeaderView.getTop());
        mOffsetAnimator.setIntValues(mLastAnimateValue, 0);
        mOffsetAnimator.start();
    }

    private void animateOffsetToEnd() {
        mLastAnimateValue = 0;
        mOffsetAnimator.setIntValues(0, Math.abs(mHeaderView.getTop()));
        mOffsetAnimator.start();
    }

    /**
     * Expansion Header View to Maximum Size
     */
    public void animateToEnd() {
        animateOffsetToEnd();
    }

    /**
     * Expansion Header View to Minimum Size
     */
    public void animateToStart() {
        animateOffsetToStart();
    }

    private void fling(float velocityY) {
        // 500 代表每秒滑动500PX
        if (Math.abs(velocityY) > 500) {
            if (velocityY > 0) { // 下滑
                animateOffsetToEnd();
            } else { // 上滑
                animateOffsetToStart();
            }
        } else {
            if (velocityY > 0) {
                animateOffsetTo(0.75f);
            } else {
                animateOffsetTo(0.25f);
            }
        }
        if (mOnFlingListener != null) {
            mOnFlingListener.onFling(velocityY);
        }
    }

    private void scroll(int dy) {
        int offset = dy;

        if (dy < 0) { // 上推
            // 推到顶部不在滑动
            if (mContentView.getTop() <= mCollapseHeight) {
                return;
            }
            int scrollAbleDistance = mContentView.getTop() - mCollapseHeight;
            // 上推的剩余空间不够dy,能滑多少就滑多少。
            if (scrollAbleDistance < Math.abs(dy)) {
                offset = -scrollAbleDistance;
            }
        }
        if (dy > 0) { // 下拉
            // 下拉到顶部不在滑动
            if (mHeaderView.getTop() >= 0) {
                return;
            }

            int scrollAbleDistance = Math.abs(mHeaderView.getTop());
            // 下拉的剩余空间不够DY
            if (scrollAbleDistance < dy) {
                offset = scrollAbleDistance;
            }
        }

        ViewCompat.offsetTopAndBottom(mHeaderView, offset);
        ViewCompat.offsetTopAndBottom(mContentView, offset);

        mCurOffset = mHeaderView.getTop();

        if (mOnOffsetChangedListener != null) {
            mOnOffsetChangedListener.onOffsetChanged(-mCurOffset, mHeaderView.getHeight() - mCollapseHeight);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int availableHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (availableHeight == 0) {
            availableHeight = getHeight();
        }

        final int height = availableHeight - mCollapseHeight;
        final int contentViewHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height,
                mContentView.getLayoutParams().height == ViewGroup.LayoutParams.MATCH_PARENT
                        ? MeasureSpec.EXACTLY
                        : MeasureSpec.AT_MOST);
        measureChild(mContentView, widthMeasureSpec, contentViewHeightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        // fix bug: onLayout be called, we shoud reset child offset.
        ViewCompat.offsetTopAndBottom(mHeaderView, mCurOffset);
        ViewCompat.offsetTopAndBottom(mContentView, mCurOffset);
    }

    @Override
    protected void onFinishInflate() {
        final int CHILD_COUNT = getChildCount();
        if (CHILD_COUNT != 2) {
            throw new IllegalStateException("CollapseableLayout request only 2 child!");
        } else {
            if (mHeaderId != INVALID_VALUE && mHeaderView == null) {
                mHeaderView = findViewById(mHeaderId);
            }
            if (mContentId != INVALID_VALUE && mContentView == null) {
                mContentView = findViewById(mContentId);
            }
            if (mScrollViewId != INVALID_VALUE && mRealScrollAbleView == null) {
                mRealScrollAbleView = findViewById(mScrollViewId);
            }
        }

        if (mHeaderView == null || mContentView == null) {
            throw new IllegalStateException("CollapseableLayout request header and content.");
        }
        super.onFinishInflate();
    }

    /**
     * 判断是否可以继续下滑仅支持ListView 和 RecyclerView
     */
    private boolean canChildScrollDown() {
        if (mRealScrollAbleView != null) {
            View child;
            if (mRealScrollAbleView instanceof ListView) {
                ListView listView = (ListView) mRealScrollAbleView;
                child = listView.getChildAt(0);
                return child != null && child.getTop() < 0;
            }
            if (mRealScrollAbleView instanceof RecyclerView) {
                RecyclerView recyclerView = (RecyclerView) mRealScrollAbleView;
                child = recyclerView.getLayoutManager().getChildAt(0);
                return child != null && child.getTop() < 0;
            }
            if (mRealScrollAbleView instanceof NestedScrollView) {
                NestedScrollView nestedScrollView = (NestedScrollView) mRealScrollAbleView;
                return nestedScrollView.canScrollVertically(-1);
            }
        }
        return false;
    }

    /**
     * 是否可以折叠
     */
    public void setCollapseEnable(boolean collapseEnable) {
        this.mCollapseEnable = collapseEnable;
    }

    /**
     * 是否灵活伸缩,松手自动伸缩
     */
    public void setFlexible(boolean isFlexible) {
        this.mFlexible = isFlexible;
    }

    /**
     * 替换可以进行独立滚动的ChildView
     */
    public void resetRealScrollAbleView(int resId) {
        if (resId != INVALID_VALUE) {
            mScrollViewId = resId;
            mRealScrollAbleView = findViewById(resId);
        }
    }

    public interface IOnOffsetChangedListener {
        void onOffsetChanged(int verticalOffset, int maxOffset);
    }

    private IOnOffsetChangedListener mOnOffsetChangedListener;

    public void addOnOffsetChangedListener(IOnOffsetChangedListener listener) {
        this.mOnOffsetChangedListener = listener;
    }

    public interface IOnFlingListener {
        /**
         * @param velocityY velocityY < 0 : 上滑 , 反之下滑
         */
        void onFling(float velocityY);
    }

    private IOnFlingListener mOnFlingListener;

    public void addOnFlingListener(IOnFlingListener onFlingListener) {
        this.mOnFlingListener = onFlingListener;
    }
}
