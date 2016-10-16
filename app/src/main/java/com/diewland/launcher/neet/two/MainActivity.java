package com.diewland.launcher.neet.two;

import android.app.WallpaperManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    public static String PACKAGE_NAME;
    public static String SORT_TYPE_SCORE = "SCORE";
    public static String SORT_TYPE_TS    = "TS";

    private DrawerLayout drawer;
    private TextView bg;
    private LinearLayout ll;
    private LinearLayout.LayoutParams lp;
    private EditText txt_search;
    private Button btn_clear;
    private PackageManager manager;
    private HashMap<String, Item> app_list;
    private HashMap<String, Drawable> icon_list;
    private List<Item> sorted_items;
    private SharedPreferences mPrefs;
    private String sort_type = SORT_TYPE_SCORE;

    private String TAG = "DIEWLAND";
    private String backup_filename = "neet.dat";
    private String deli = "###";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // hide action bar
        getSupportActionBar().hide();

        // get package name
        PACKAGE_NAME = getApplicationContext().getPackageName();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // core vars
        app_list = new HashMap<String, Item>();
        icon_list = new HashMap<String, Drawable>();
        manager = getPackageManager();
        mPrefs = getPreferences(MODE_PRIVATE);

        // initialize layout vars
        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        bg = (TextView)findViewById(R.id.bg);
        ll = (LinearLayout)findViewById(R.id.ll);
        lp = new LinearLayout.LayoutParams(
                 LinearLayout.LayoutParams.MATCH_PARENT, // width
                 LinearLayout.LayoutParams.WRAP_CONTENT  // height
        );
        int neet_btm_margin = (int)getResources().getDimension(R.dimen.neet_btn_margin);
        lp.setMargins(0, 0, 0, neet_btm_margin);
        txt_search = (EditText)findViewById(R.id.txt_search);
        btn_clear = (Button)findViewById(R.id.btn_clear);

        // set wallpaper
        final WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
        final Drawable wallpaperDrawable = wallpaperManager.getDrawable();
        //getWindow().setBackgroundDrawable(wallpaperDrawable);
        ImageView iv = (ImageView)findViewById(R.id.bg2);
        iv.setImageDrawable(wallpaperDrawable);

        // manage mPrefs
        Toast.makeText(this, "Loading app list..", Toast.LENGTH_LONG).show();
        load_data(); // update latest app to app_list
        save_data(); // save app_list to mPrefs

        // draw items
        reload_items();

        // instant googling
        ll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(ll.getChildCount() == 0) {
                    Toast.makeText(getApplicationContext(), "Googling..", Toast.LENGTH_SHORT).show();
                    String q = txt_search.getText().toString();
                    Uri uri = Uri.parse("https://www.google.co.th/search?q=" + q);
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                }
            }
        });

        // bind search bar
        txt_search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                reload_items();
            }

        });
        btn_clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                txt_search.setText("");
                reload_items();
            }
        });
    }

    /*** Utility ***/

    private void save_data(){
        SharedPreferences.Editor prefsEditor = mPrefs.edit();
        Gson gson = new Gson();
        for(Item app : app_list.values()){
            String json = gson.toJson(app);
            prefsEditor.putString(app.getPackage(), json);
        }
        prefsEditor.putString("sort_type", sort_type);
        prefsEditor.commit();
    }

    private void load_data(){

        Gson gson = new Gson();

        // maintain data
        app_list.clear();
        Intent i = new Intent(Intent.ACTION_MAIN, null);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> availableActivities = manager.queryIntentActivities(i, 0);
        for(ResolveInfo ri:availableActivities){
            String title = ri.loadLabel(manager).toString();
            String pkg   = ri.activityInfo.packageName;
            Drawable icon = ri.loadIcon(manager);

            // not include this app
            if(pkg.equals(PACKAGE_NAME)){
                continue;
            }
            String json = mPrefs.getString(pkg, "");
            if(json.equals("")){ // new
                app_list.put(pkg, new Item(title, pkg));
            }
            else {
                Item app = gson.fromJson(json, Item.class);
                app_list.put(pkg, new Item(title, pkg, app.getScore(), app.getTS()));
            }

            // collect icons
            icon_list.put(pkg, icon);
        }

        sort_type = mPrefs.getString("sort_type", SORT_TYPE_SCORE);
    }

    private void reload_items(){

        // remove all first
        ll.removeAllViews();

        // filter items
        sorted_items = Util.sort_by(sort_type, app_list.values());
        sorted_items = Util.filter(sorted_items, txt_search.getText().toString());

        // handle background text
        if(sorted_items.size() > 0){
            bg.setText("");
        }
        else {
            bg.setText("Double Tap\nto Search");
        }

        // draw text buttons
        for(int seq=0; seq<sorted_items.size(); seq++){
            Item info = sorted_items.get(seq);

            Button btn = new Button(this);
            btn.setText(info.getTitle());

            // button style
            btn.setTextSize(22);
            btn.setGravity(Gravity.LEFT);
            btn.setGravity(Gravity.CENTER_VERTICAL);
            btn.setPadding(40, 30, 20, 30);
            btn.setTransformationMethod(null);
            btn.getBackground().setAlpha(128); // 256 -> 100% / 128 -> 50 %

            try {
                Bitmap bitmap = ((BitmapDrawable) icon_list.get(info.getPackage())).getBitmap();
                Drawable d = new BitmapDrawable(getResources(), bitmap);
                d.setBounds(0, 0, d.getMinimumWidth(), d.getMinimumHeight());
                btn.setCompoundDrawablesWithIntrinsicBounds(null, null, d, null);
            }
            catch(Exception e){
                Log.w(TAG, "Skip uninstall app --> " + info.getPackage());
                continue;
            }
            btn.setTag(seq);

            // click button
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Item info = sorted_items.get((Integer) v.getTag());

                    // collect app stat
                    info.click();

                    // lunch app
                    Intent LaunchIntent = getPackageManager().getLaunchIntentForPackage(info.getPackage());
                    startActivity(LaunchIntent);
                }
            });

            // add item
            ll.addView(btn, lp);
        }
    }

    private File get_backup_file(){
        String externalStorage = Environment.getExternalStorageDirectory().getAbsolutePath();
        return new File(externalStorage + File.separator + backup_filename);
    }

    private void backup() throws IOException {
        File file = get_backup_file();
        FileOutputStream f = new FileOutputStream(file);
        PrintWriter pw = new PrintWriter(f);
        for(Item app : app_list.values()){
            pw.println(app.getPackage()
                    + deli + app.getTitle()
                    + deli + app.getScore()
                    + deli + app.getTS()
            );
        }

        // TODO *** make visible from windows explorer ( after write, before flush & close )
        // TODO *** MediaScannerConnection.scanFile(this, new String[] {file.toString()}, null, null);
        // TODO *** MediaScannerConnection.scanFile(this, new String[]{ externalStorage }, null, null);

        pw.flush();
        pw.close();
        f.close();

        // toast some message
        Toast.makeText(getApplicationContext(), "Backup to " + file.toString(), Toast.LENGTH_LONG).show();
    }

    private void restore() throws IOException {
        // prepare file
        File file = get_backup_file();
        InputStream is = new FileInputStream(file);
        String UTF8 = "utf8";
        int BUFFER_SIZE = 8192;
        BufferedReader br = new BufferedReader(new InputStreamReader(is, UTF8), BUFFER_SIZE);
        String str;

        // reload app_list
        app_list.clear();
        while ((str = br.readLine()) != null) {
            String[] data = str.split(deli);
            if(data.length == 3){
                app_list.put(data[0], new Item(data[1], data[0], Integer.parseInt(data[2]), Long.valueOf(0)));
            }
            else if(data.length == 4){
                app_list.put(data[0], new Item(data[1], data[0], Integer.parseInt(data[2]), Long.parseLong(data[3])));
            }
        }
        reload_items();

        // toast some message
        Toast.makeText(getApplicationContext(), "Restore complete", Toast.LENGTH_LONG).show();
    }

    private void debug(){
        for(Item app : app_list.values()){
            Log.d(TAG, app.getPackage() + "\t" + app.getTitle() + "\t" + app.getScore());
        }
    }

    /*** App Events ***/

    @Override
    protected void onPause() {
        save_data();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            // ignore back press
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_sort_by_freq) {
            sort_type = SORT_TYPE_SCORE;
            reload_items();
            Toast.makeText(this, "Sort by Frequency", Toast.LENGTH_LONG).show();
        } else if (id == R.id.nav_sort_by_recent) {
            sort_type = SORT_TYPE_TS;
            reload_items();
            Toast.makeText(this, "Sort by Recent", Toast.LENGTH_LONG).show();
        } else if (id == R.id.nav_restart) {
            finish();
            startActivity(getIntent());
        } else if (id == R.id.nav_backup) {
            try {
                backup();
            } catch(Exception e){
                throw new RuntimeException(e);
            }
        } else if (id == R.id.nav_restore) {
            try {
                restore();
            } catch(Exception e){
                throw new RuntimeException(e);
            }
        }
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    // toggle side drawer with setting button
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if ( keyCode == KeyEvent.KEYCODE_MENU ) {
            if (drawer.isDrawerOpen(GravityCompat.START)) {
                drawer.closeDrawer(GravityCompat.START);
            } else {
                drawer.openDrawer(GravityCompat.START);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
