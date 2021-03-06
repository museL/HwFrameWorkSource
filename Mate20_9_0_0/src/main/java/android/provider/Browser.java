package android.provider;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BrowserContract.History;
import android.webkit.WebIconDatabase.IconListener;

public class Browser {
    public static final Uri BOOKMARKS_URI = Uri.parse("content://browser/bookmarks");
    public static final String EXTRA_APPLICATION_ID = "com.android.browser.application_id";
    public static final String EXTRA_CREATE_NEW_TAB = "create_new_tab";
    public static final String EXTRA_HEADERS = "com.android.browser.headers";
    public static final String EXTRA_SHARE_FAVICON = "share_favicon";
    public static final String EXTRA_SHARE_SCREENSHOT = "share_screenshot";
    public static final String[] HISTORY_PROJECTION = new String[]{"_id", "url", "visits", "date", "bookmark", "title", "favicon", "thumbnail", "touch_icon", "user_entered"};
    public static final int HISTORY_PROJECTION_BOOKMARK_INDEX = 4;
    public static final int HISTORY_PROJECTION_DATE_INDEX = 3;
    public static final int HISTORY_PROJECTION_FAVICON_INDEX = 6;
    public static final int HISTORY_PROJECTION_ID_INDEX = 0;
    public static final int HISTORY_PROJECTION_THUMBNAIL_INDEX = 7;
    public static final int HISTORY_PROJECTION_TITLE_INDEX = 5;
    public static final int HISTORY_PROJECTION_TOUCH_ICON_INDEX = 8;
    public static final int HISTORY_PROJECTION_URL_INDEX = 1;
    public static final int HISTORY_PROJECTION_VISITS_INDEX = 2;
    public static final String INITIAL_ZOOM_LEVEL = "browser.initialZoomLevel";
    private static final String LOGTAG = "browser";
    private static final int MAX_HISTORY_COUNT = 250;
    public static final String[] SEARCHES_PROJECTION = new String[]{"_id", "search", "date"};
    public static final int SEARCHES_PROJECTION_DATE_INDEX = 2;
    public static final int SEARCHES_PROJECTION_SEARCH_INDEX = 1;
    public static final Uri SEARCHES_URI = Uri.parse("content://browser/searches");
    public static final String[] TRUNCATE_HISTORY_PROJECTION = new String[]{"_id", "date"};
    public static final int TRUNCATE_HISTORY_PROJECTION_ID_INDEX = 0;
    public static final int TRUNCATE_N_OLDEST = 5;

    public static class BookmarkColumns implements BaseColumns {
        public static final String BOOKMARK = "bookmark";
        public static final String CREATED = "created";
        public static final String DATE = "date";
        public static final String FAVICON = "favicon";
        public static final String THUMBNAIL = "thumbnail";
        public static final String TITLE = "title";
        public static final String TOUCH_ICON = "touch_icon";
        public static final String URL = "url";
        public static final String USER_ENTERED = "user_entered";
        public static final String VISITS = "visits";
    }

    public static class SearchColumns implements BaseColumns {
        public static final String DATE = "date";
        public static final String SEARCH = "search";
        @Deprecated
        public static final String URL = "url";
    }

    public static final void saveBookmark(Context c, String title, String url) {
    }

    public static final void sendString(Context context, String string) {
        sendString(context, string, context.getString(17041065));
    }

    public static final void sendString(Context c, String stringToSend, String chooserDialogTitle) {
        Intent send = new Intent("android.intent.action.SEND");
        send.setType("text/plain");
        send.putExtra("android.intent.extra.TEXT", stringToSend);
        try {
            Intent i = Intent.createChooser(send, chooserDialogTitle);
            i.setFlags(268435456);
            c.startActivity(i);
        } catch (ActivityNotFoundException e) {
        }
    }

    public static final Cursor getAllBookmarks(ContentResolver cr) throws IllegalStateException {
        return new MatrixCursor(new String[]{"url"}, 0);
    }

    public static final Cursor getAllVisitedUrls(ContentResolver cr) throws IllegalStateException {
        return new MatrixCursor(new String[]{"url"}, 0);
    }

    private static final void addOrUrlEquals(StringBuilder sb) {
        sb.append(" OR url = ");
    }

    private static final Cursor getVisitedLike(ContentResolver cr, String url) {
        StringBuilder whereClause;
        boolean secure = false;
        String compareString = url;
        if (compareString.startsWith("http://")) {
            compareString = compareString.substring(7);
        } else if (compareString.startsWith("https://")) {
            compareString = compareString.substring(8);
            secure = true;
        }
        if (compareString.startsWith("www.")) {
            compareString = compareString.substring(4);
        }
        if (secure) {
            whereClause = new StringBuilder("url = ");
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("https://");
            stringBuilder.append(compareString);
            DatabaseUtils.appendEscapedSQLString(whereClause, stringBuilder.toString());
            addOrUrlEquals(whereClause);
            stringBuilder = new StringBuilder();
            stringBuilder.append("https://www.");
            stringBuilder.append(compareString);
            DatabaseUtils.appendEscapedSQLString(whereClause, stringBuilder.toString());
        } else {
            whereClause = new StringBuilder("url = ");
            DatabaseUtils.appendEscapedSQLString(whereClause, compareString);
            addOrUrlEquals(whereClause);
            String wwwString = new StringBuilder();
            wwwString.append("www.");
            wwwString.append(compareString);
            wwwString = wwwString.toString();
            DatabaseUtils.appendEscapedSQLString(whereClause, wwwString);
            addOrUrlEquals(whereClause);
            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append("http://");
            stringBuilder2.append(compareString);
            DatabaseUtils.appendEscapedSQLString(whereClause, stringBuilder2.toString());
            addOrUrlEquals(whereClause);
            stringBuilder2 = new StringBuilder();
            stringBuilder2.append("http://");
            stringBuilder2.append(wwwString);
            DatabaseUtils.appendEscapedSQLString(whereClause, stringBuilder2.toString());
        }
        return cr.query(History.CONTENT_URI, new String[]{"_id", "visits"}, whereClause.toString(), null, null);
    }

    public static final void updateVisitedHistory(ContentResolver cr, String url, boolean real) {
    }

    @Deprecated
    public static final String[] getVisitedHistory(ContentResolver cr) {
        return new String[0];
    }

    public static final void truncateHistory(ContentResolver cr) {
    }

    public static final boolean canClearHistory(ContentResolver cr) {
        return false;
    }

    public static final void clearHistory(ContentResolver cr) {
    }

    public static final void deleteHistoryTimeFrame(ContentResolver cr, long begin, long end) {
    }

    public static final void deleteFromHistory(ContentResolver cr, String url) {
    }

    public static final void addSearchUrl(ContentResolver cr, String search) {
    }

    public static final void clearSearches(ContentResolver cr) {
    }

    public static final void requestAllIcons(ContentResolver cr, String where, IconListener listener) {
    }
}
