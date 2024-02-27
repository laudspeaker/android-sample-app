package com.laudspeaker.myapplication;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.FirebaseApp;
import com.laudspeaker.android.ConnectListener;
import com.laudspeaker.android.LaudspeakerAndroid;
import com.laudspeaker.myapplication.databinding.ActivityMainBinding;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseApp.initializeApp(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        binding.fab.setOnClickListener(view -> Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG).setAnchorView(R.id.fab).setAction("Action", null).show());

        LaudspeakerAndroid laudspeakerAndroid = new LaudspeakerAndroid(this, getPreferences(MODE_PRIVATE), "eSLajTyYMURIvk13z3ibXmfgPAGybsRDvejVPpx4","https://fbdade1d813f.ngrok.app",true);


        Button identify_button = findViewById(R.id.identify_button);
        identify_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                laudspeakerAndroid.identify("mahamad@laudspeaker.com");
            }
        });

        Button fire_button = findViewById(R.id.fire_button);
        fire_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                laudspeakerAndroid.fire("example_event");
            }
        });


        Switch switchToggle = findViewById(R.id.switchToggle);
        switchToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Prevent the switch from changing state immediately
                buttonView.setChecked(!isChecked);
                Map<String, Object> map = new HashMap<>();
                map.put("notification_preferences", isChecked);
                laudspeakerAndroid.set(map);
                buttonView.setChecked(isChecked);
            }
        });


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp();
    }
}