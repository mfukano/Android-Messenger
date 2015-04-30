package com.dealfaro.luca.clicker;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;


public class MainActivity extends ActionBarActivity {
    Location lastLocation;
    private static final String LOG_TAG = "lclicker";
    private static final String SERVER_URL_PREFIX = "https://luca-teaching.appspot.com/store/default/";
    private static final float GOOD_ACCURACY_METERS = 100;

    private String lat;
    private String lng;

    // To remember the post we received.
    public static final String PREF_POSTS = "pref_posts";

    // Uploader.
    private ServerCall uploader;
    private ProgressDialog progress;

    private class ListElement {
        ListElement() {}
        public String textLabel;
        public String timeText;
        public String timeStamp;
        public String messageID;
    }

    private ArrayList<ListElement> aList;

    private class MyAdapter extends ArrayAdapter<ListElement> {
        int resource;
        Context context;

        public MyAdapter(Context _context, int _resource, List<ListElement> items) {
            super(_context, _resource, items);
            resource = _resource;
            context = _context;
            this.context = _context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout newView;
            ListElement w = getItem(position);

            // Inflate a new view if necessary.
            if (convertView == null) {
                newView = new LinearLayout(getContext());
                String inflater = Context.LAYOUT_INFLATER_SERVICE;
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(inflater);
                vi.inflate(resource, newView, true);
            } else {
                newView = (LinearLayout) convertView;
            }

            // Fills in the view.
            TextView tv = (TextView) newView.findViewById(R.id.itemText);
            tv.setText(w.textLabel);

            TextView cv = (TextView) newView.findViewById(R.id.timeSince);
            cv.setText(w.timeText);

            // Set a listener for the whole list item.
            newView.setTag(w.messageID);
            newView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String s = v.getTag().toString();
                    int duration = Toast.LENGTH_SHORT;
                    Toast toast = Toast.makeText(context, s, duration);
                    toast.show();
                }
            });
            return newView;
        }
    }

    private MyAdapter aa;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        aList = new ArrayList<>();
        aa = new MyAdapter(this, R.layout.list_element, aList);
        ListView myListView = (ListView) findViewById(R.id.listView);
        myListView.setAdapter(aa);
        aa.notifyDataSetChanged();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // First super, then do stuff.
        // Let us display the previous posts, if any.
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String result = settings.getString(PREF_POSTS, null);
        if (result != null) {
            displayResult(result);
        }
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
    }

    @Override
    protected void onPause() {
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(locationListener);
        // Stops the upload if any.
        if (uploader != null) {
            uploader.cancel(true);
            uploader = null;
        }
        super.onPause();
    }

    /**
     * Listens to the location, and gets the most precise recent location.
     */
    LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            lastLocation = location;
            TextView tv = (TextView) findViewById(R.id.accuracyView);
            try {
                lat = Double.toString(lastLocation.getLatitude());
                lng = Double.toString(lastLocation.getLongitude());
            } catch (IllegalStateException e) {
                lat = "Sorry, can't";
                lng = "find location";
            } finally {
                String s = String.format("%s m\n%s m", lat, lng);
                tv.setText(s);
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    };

    /*
     *  Progress dialog loader code
     */
    public void showLoader() {
        if (progress == null) {
            progress = new ProgressDialog(this);
            progress.setTitle("Loadin'");
            progress.setMessage("Hold yer horses!");
        }
        progress.show();
    }

    public void dismissLoader() {
        if (progress != null && progress.isShowing()) {
            progress.dismiss();
        }
    }

    /*
     *  Toast helper method
     */
    public void toastIt(String s) {
        Context c = getApplicationContext();
        int toastingTime = Toast.LENGTH_SHORT;
        Toast rye = Toast.makeText(c, s, toastingTime);
        rye.show();
    }

    /*
     *  Wifi signal tester method
     */
    public boolean wifiTest() {
        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return mWifi.isConnected();
    }


    /*
     *   Random string builder code pulled from StackOverflow
     *   This snippet will help to create message ID numbers
     *   src: http://stackoverflow.com/a/5683359
     */
    String randomString(final int length) {
        char[] chars = "aAbBcCdDeEfFgGhHiIjJkKlLmMnNoOpPqQrRsStTuUvVwWxXyYzZ1234567890".toCharArray();
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            char c = chars[random.nextInt(chars.length)];
            sb.append(c);
        }
        return sb.toString();
    }

    /*
     *  Refresh function pulls newly posted messages onto the screen after checking
     *  to see if the Wifi connection is still good and the accuracy is in range.
     */
    public void refresh(View v) {
        if ((lastLocation.getAccuracy() > GOOD_ACCURACY_METERS) || (!wifiTest())) {
            toastIt("Can't refresh, try again later");
        } else {
            showLoader();
            PostMessageSpec myCallSpec = new PostMessageSpec();
            myCallSpec.url = SERVER_URL_PREFIX + "get_local";
            myCallSpec.context = MainActivity.this;
            // Let's add the parameters.
            HashMap<String, String> m = new HashMap<>();
            m.put("lat", lat);
            m.put("lng", lng);

            myCallSpec.setParams(m);
            // Actual server call.
            if (uploader != null) {
                // There was already an upload in progress.
                uploader.cancel(true);
            }
            uploader = new ServerCall();
            uploader.execute(myCallSpec);
        }
    }

    /*
     *  clickButton posts a new message constructed locally and pulls new messages
     *  posted to the server after checking to see if the Wifi connection is still
     *  good and the accuracy is in range.
     */
    public void clickButton(View v) {
        // Get the text we want to send.
        EditText et = (EditText) findViewById(R.id.editText);
        String msg = et.getText().toString();

        // Condition for empty string
        if (msg == null || msg.trim().isEmpty()) {
            toastIt("C'mon, write something!");
        } // Condition for inaccurate parameters / disconnected device
        else if ((lastLocation.getAccuracy() > GOOD_ACCURACY_METERS) || (!wifiTest())) {
            toastIt("Can't post, try again later");
            InputMethodManager inputManager = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
            et.clearFocus();
        } else {
            // Else, we start the call.
            showLoader();
            PostMessageSpec myCallSpec = new PostMessageSpec();
            myCallSpec.url = SERVER_URL_PREFIX + "put_local";
            myCallSpec.context = MainActivity.this;
            // Let's add the parameters.
            HashMap<String, String> m = new HashMap<>();
            m.put("lat", lat);
            m.put("lng", lng);
            m.put("msgid", randomString(8));
            m.put("msg", msg);

            myCallSpec.setParams(m);
            // Actual server call.
            if (uploader != null) {
                // There was already an upload in progress.
                uploader.cancel(true);
            }
            uploader = new ServerCall();
            uploader.execute(myCallSpec);

        /*  Snippet of code to remove focus from editText upon posting a message.
         *  http://stackoverflow.com/a/17491896
         */
            InputMethodManager inputManager = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
            et.setText("");
            et.clearFocus();
        }
    }

    //parse timestamp string and return the relevant time difference (1d, 1h, 30 min, etc)
    private String getRelevantTimeDiff(String ts){
        Date timestampDate;
        DateFormat targetFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss");
        targetFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String formattedDate = "error";
        long s, m, h, d;
        try { //attempt to parse date
            timestampDate = targetFormat.parse(ts);
        } catch (ParseException e) {
            e.printStackTrace();
            return formattedDate;
        }
        // The server seems to be sending us localized timestamps instead of UTC, so I add 7h
        Date now = new Date();
        //The server gives us a localized timestamp instead of UTC, so I subtract 7h
        Date msg =  timestampDate;

        long diffInSeconds = (now.getTime() - msg.getTime()) / 1000;
        s = (diffInSeconds >= 60 ? diffInSeconds % 60 : diffInSeconds);
        m = (diffInSeconds = (diffInSeconds / 60)) >= 60 ? diffInSeconds % 60 : diffInSeconds;
        h = (diffInSeconds = (diffInSeconds / 60)) >= 24 ? diffInSeconds % 24 : diffInSeconds;
        d = (diffInSeconds / 24);

        if(d > 0)         //only return days if its been over 24h
            return d + "d";
        else if(h > 0)    //only return hours if its been over 1h
            return h + "h";
        else if(m > 0)    //only return minutes if its been over 1m
            return m + "m";
        else if(s > 0)    //only return seconds if its been under 1m
            return s + "s";
        else
            return "now";
    }

    /*
     *  Applies all properties to be updated to every element of the list view.
     */
    private void displayResult(String result) {
        Gson gson = new Gson();
        MessageList ml = gson.fromJson(result, MessageList.class);
        // Fills aList, so we can fill the listView.
        aList.clear();
        for (int i = 0; i < ml.messages.length; i++) {
            ListElement ael = new ListElement();
            /* adds the message body to the text label in the app view
            -- pulls this from the message class by indexing into the message
            -- list and obtaining the type */
            String formattedDate = getRelevantTimeDiff(ml.messages[i].ts);
            ael.textLabel = ml.messages[i].msg;     // message body
            ael.timeText = formattedDate;           // timestamp text
            ael.messageID = ml.messages[i].msgid;   // message ID
            ael.timeStamp = ml.messages[i].ts;      // timestamp data
            aList.add(ael);
        }
        ListView myListView = (ListView) findViewById(R.id.listView);
        myListView.setAdapter(aa);
        aa.notifyDataSetChanged();
        dismissLoader();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * This class is used to do the HTTP call, and it specifies how to use the result.
     */
    class PostMessageSpec extends ServerCallSpec {
        @Override
        public void useResult(Context context, String result) {
            if (result == null) {
                // Do something here, e.g. tell the user that the server cannot be contacted.
                Log.i(LOG_TAG, "The server call failed.");
            } else {
                // Translates the string result, decoding the Json.
                Log.i(LOG_TAG, "Received string: " + result);
                displayResult(result);
                // Stores in the settings the last messages received.
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor editor = settings.edit();
                editor.putString(PREF_POSTS, result);
                editor.commit();
            }
        }
    }
}
