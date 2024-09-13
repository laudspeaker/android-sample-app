package com.laudspeaker.myapplication

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.Navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.AppBarConfiguration.Builder
import androidx.navigation.ui.NavigationUI.navigateUp
import androidx.navigation.ui.NavigationUI.setupActionBarWithNavController
import com.google.firebase.FirebaseApp
import com.laudspeaker.android.Laudspeaker
import com.laudspeaker.myapplication.databinding.ActivityMainBinding
import org.koin.android.ext.android.inject

class MainActivity : AppCompatActivity() {
    private var appBarConfiguration: AppBarConfiguration? = null
    private var binding: ActivityMainBinding? = null

    private val laudspeaker: Laudspeaker by inject()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        binding = ActivityMainBinding.inflate(
            layoutInflater
        )
        setContentView(binding!!.getRoot())
        setSupportActionBar(binding!!.toolbar)
        val navController = findNavController(this, R.id.nav_host_fragment_content_main)
        appBarConfiguration = Builder(navController.graph).build()
        setupActionBarWithNavController(this, navController, appBarConfiguration!!)

        laudspeaker.setNotificationIcon(R.drawable.ic_launcher_background)
        laudspeaker.handlePushOpened(intent)
        val identify_button = findViewById<Button>(R.id.identify_button)
        identify_button.setOnClickListener {
            val myMap: MutableMap<String, Any> = HashMap()
            myMap["time"] = System.currentTimeMillis()
            laudspeaker.identify("mahamad@laudspeaker.com", myMap)
        }
        val fire_button = findViewById<Button>(R.id.fire_button)
        fire_button.setOnClickListener {
            val myMap: MutableMap<String, Any> = HashMap()
            myMap["time"] = System.currentTimeMillis()
            println("Laudspeaker API: inside capture")
            laudspeaker.fire("hello", myMap)
        }
        val switchToggle = findViewById<Switch>(R.id.switchToggle)
        switchToggle.setOnCheckedChangeListener { buttonView, isChecked -> // Prevent the switch from changing state immediately
            buttonView.isChecked = !isChecked
            val map: MutableMap<String, Any> = HashMap()
            map["notification_preferences"] = isChecked
            laudspeaker.set(map)
            buttonView.isChecked = isChecked
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId
        return if (id == R.id.action_settings) {
            true
        } else super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(this, R.id.nav_host_fragment_content_main)
        return navigateUp(navController, appBarConfiguration!!) || super.onSupportNavigateUp()
    }
}