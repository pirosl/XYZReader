package com.example.xyzreader.ui;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.app.ShareCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;

import junit.framework.Assert;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * An activity representing a single Article detail screen, letting you swipe between articles.
 */
public class ArticleDetailActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor>, AppBarLayout.OnOffsetChangedListener {

    private static final String TAG = ArticleDetailActivity.class.getSimpleName();

    private static final float PERCENTAGE_TO_SWITCH_TITLES = 0.9f;
    private static final int MAX_PARGRAPHS_LOADED_PER_HANDLER = 3;
    private static final int ALPHA_ANIMATIONS_DURATION = 200;
    private static final int SHORT_ALPHA_ANIMATIONS_DURATION = 1;
    private boolean mIsTheTitleVisible = false;
    private boolean mIsTheTitleContainerVisible = true;

    private Cursor mCursor;
    private long mItemId;
    private int paragraphTmp;

    @BindView(R.id.article_title)
    TextView titleView;
    @BindView(R.id.toolbar_article_title)
    TextView toolbarTitleView;
    @BindView(R.id.article_byline)
    TextView bylineView;
    @BindView(R.id.body_content)
    LinearLayout bodyContent;

    @BindView(R.id.photo)
    DynamicHeightNetworkImageView mPhotoView;
    @BindView(R.id.collapsing_toolbar)
    CollapsingToolbarLayout mCollapsingToolbarLayout;
    @BindView(R.id.app_bar_layout)
    AppBarLayout mAppBarLayout;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2, 1, 1);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
        setContentView(R.layout.activity_article_detail);

        ButterKnife.bind(this);
        Assert.assertNotNull(titleView);
        Assert.assertNotNull(toolbarTitleView);
        Assert.assertNotNull(bylineView);
        Assert.assertNotNull(bodyContent);
        Assert.assertNotNull(mPhotoView);
        Assert.assertNotNull(mCollapsingToolbarLayout);
        Assert.assertNotNull(mAppBarLayout);

        // initialise the paragraphTmp with 0
        paragraphTmp = 0;

        bylineView.setMovementMethod(new LinkMovementMethod());
        mAppBarLayout.addOnOffsetChangedListener(this);

        if (savedInstanceState == null) {
            //if (getIntent() != null && getIntent().getData() != null) {
            //   mItemId = ItemsContract.Items.getItemId(getIntent().getData());
            //}
            Bundle bundle = getIntent().getExtras();

            mItemId = bundle.getLong("_id");

            getLoaderManager().initLoader(0, null, this);
        }

        final Activity activity = this;
        findViewById(R.id.share_fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(Intent.createChooser(ShareCompat.IntentBuilder.from(activity)
                        .setType("text/plain")
                        .setText(titleView.getText() + " - " + bylineView.getText())
                        .getIntent(), getString(R.string.action_share)));
            }
        });

        startAlphaAnimation(toolbarTitleView, SHORT_ALPHA_ANIMATIONS_DURATION, View.INVISIBLE);
    }

    private Date parsePublishedDate() {
        try {
            String date = mCursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
            return dateFormat.parse(date);
        } catch (ParseException ex) {
            Log.i(TAG, "Passing today's date");
            Log.e(TAG, ex.getMessage());
            return new Date();
        }
    }

    private void bindViews() {

        if (mCursor != null) {
            titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
            toolbarTitleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));

            Date publishedDate = parsePublishedDate();
            if (!publishedDate.before(START_OF_EPOCH.getTime())) {
                bylineView.setText(Html.fromHtml(
                        DateUtils.getRelativeTimeSpanString(
                                publishedDate.getTime(),
                                System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_ALL).toString()
                                + " by <font color='#ffffff'>"
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)
                                + "</font>"));

            } else {
                // If date is before 1902, just show the string
                bylineView.setText(Html.fromHtml(
                        outputFormat.format(publishedDate) + " by <font color='#ffffff'>"
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)
                                + "</font>"));

            }

            fillContent();

            mPhotoView.setImageUrl(
                    mCursor.getString(ArticleLoader.Query.PHOTO_URL),
                    ImageLoaderHelper.getInstance(ArticleDetailActivity.this).getImageLoader());
            mPhotoView.setAspectRatio(1);


        } else {
            titleView.setText(getResources().getString(R.string.error));
            bylineView.setText(getResources().getString(R.string.error));
            TextView emptyLine = new TextView(this, null, R.style.DetailBody);
            emptyLine.setTextColor(ContextCompat.getColor(this, R.color.colorPrimaryText));
            emptyLine.setText(" ");
            bodyContent.addView(emptyLine);

        }
    }

    private void fillContent() {
        final Context context = this;
        String body = mCursor.getString(ArticleLoader.Query.BODY);
        final String[] m = body.split("(\n{1,}[\r]?){2,}");
        final int paragraphNo = m.length;
        paragraphTmp = 0;
        final Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                int ii = 0;
                while (paragraphTmp < paragraphNo && ii < MAX_PARGRAPHS_LOADED_PER_HANDLER) {
                    TextView paragraph = new TextView(context, null, R.style.DetailBody);
                    paragraph.setTextColor(ContextCompat.getColor(context, R.color.colorPrimaryText));
                    String text = m[paragraphTmp];
                    text = text.replaceAll("[\r]?\n[\r]?", " ");
                    paragraph.setText(text);
                    bodyContent.addView(paragraph);
                    ++ii;
                    ++paragraphTmp;
                    TextView emptyLine = new TextView(context, null, R.style.DetailBody);
                    emptyLine.setTextColor(ContextCompat.getColor(context, R.color.colorPrimaryText));
                    emptyLine.setText(" ");
                    bodyContent.addView(emptyLine);
                }
                if (paragraphTmp < paragraphNo) {
                    handler.post(this);
                }
            }
        };
        handler.post(runnable);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newInstanceForItemId(getBaseContext(), mItemId);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        mCursor = cursor;
        if (mCursor != null && !mCursor.moveToFirst()) {
            Log.e(TAG, "Error reading item detail cursor");
            mCursor.close();
            mCursor = null;
        } else {
            bindViews();
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mCursor = null;
        bindViews();
    }


    @Override
    public void onOffsetChanged(AppBarLayout appBarLayout, int offset) {
        int maxScroll = appBarLayout.getTotalScrollRange();
        float percentage = (float) Math.abs(offset) / (float) maxScroll;

        handleAlphaOnTitle(percentage);
        handleToolbarTitleVisibility(percentage);
    }


    private void handleToolbarTitleVisibility(float percentage) {
        if (percentage >= PERCENTAGE_TO_SWITCH_TITLES) {

            if (!mIsTheTitleVisible) {
                startAlphaAnimation(toolbarTitleView, ALPHA_ANIMATIONS_DURATION, View.VISIBLE);
                mIsTheTitleVisible = true;
            }
        } else {
            if (mIsTheTitleVisible) {
                startAlphaAnimation(toolbarTitleView, ALPHA_ANIMATIONS_DURATION, View.INVISIBLE);
                mIsTheTitleVisible = false;
            }
        }
    }


    private void handleAlphaOnTitle(float percentage) {
        if (percentage >= PERCENTAGE_TO_SWITCH_TITLES) {
            if (mIsTheTitleContainerVisible) {
                startAlphaAnimation(titleView, ALPHA_ANIMATIONS_DURATION, View.INVISIBLE);
                startAlphaAnimation(bylineView, ALPHA_ANIMATIONS_DURATION, View.INVISIBLE);
                mIsTheTitleContainerVisible = false;
            }
        } else {
            if (!mIsTheTitleContainerVisible) {
                startAlphaAnimation(titleView, ALPHA_ANIMATIONS_DURATION, View.VISIBLE);
                startAlphaAnimation(bylineView, ALPHA_ANIMATIONS_DURATION, View.VISIBLE);
                mIsTheTitleContainerVisible = true;
            }
        }
    }

    public static void startAlphaAnimation(View v, long duration, int visibility) {
        AlphaAnimation alphaAnimation = (visibility == View.VISIBLE)
                ? new AlphaAnimation(0f, 1f)
                : new AlphaAnimation(1f, 0f);

        alphaAnimation.setDuration(duration);
        alphaAnimation.setFillAfter(true);
        v.startAnimation(alphaAnimation);
    }
}
