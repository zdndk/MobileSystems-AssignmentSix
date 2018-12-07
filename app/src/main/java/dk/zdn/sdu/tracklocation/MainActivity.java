package dk.zdn.sdu.tracklocation;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.BatteryManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    public static final String ERROR = "ERROR";
    public static final int REQUEST_CODE_CALLBACK_CODE = 111;
    public static final String CHARGING = "Charging";
    public static final String NOT_CHARGING = "Not charging";
    private FusedLocationProviderClient _fusedLocationClient;
    private LocationRequest _fusedLocationRequest;
    private SensorManager _sensorManager;
    private Sensor _orientationSensor;
    private BroadcastReceiver _batteryLevelReceiver;
    private static final int _fastCycleScantime = 5000; //ms
    private static final int _slowCycleScantime = 15000; //ms
    private int _currentCycle = _slowCycleScantime; //bootstrap with slow cycle;


    //coordinateViews
    private TextView _coordinateLongitudeTextField;
    private TextView _coordinateLatitudeTextField;
    private Button _coordinateUpdateButton;

    //orientationViews
    private TextView _orientationXTextField;
    private TextView _orientationYTextField;
    private TextView _orientationZTextField;

    //batteryViews
    private TextView _chargingTextField;
    private TextView _scantimeTextField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SetupViews();
        SetupButtonHandlers();
        SetupLocationService();
        SetupOrientationService();
        SetupBatteryLevelMonitor();

    }

    private void SetupBatteryLevelMonitor() {
        _batteryLevelReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                boolean charging = intent.getBooleanExtra(BatteryManager.ACTION_CHARGING, false);
                if (charging) {
                    _chargingTextField.setText(CHARGING);
                    _currentCycle = _fastCycleScantime;

                } else  {
                    _chargingTextField.setText(NOT_CHARGING);
                    _currentCycle = _slowCycleScantime;
                }
                _scantimeTextField.setText(_currentCycle);
                ConfigureFusedLocationClient(); //reconfigure senstorconfiguration
            }
        };
        IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(_batteryLevelReceiver, batteryLevelFilter);
    }

    private void SetupButtonHandlers() {
        _coordinateUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UpdateFusedClient();
            }
        });
    }

    @SuppressLint("MissingPermission") //Handled in code
    private void UpdateFusedClient()
    {
        _fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, getOnSuccessListener())
                .addOnFailureListener(this, getOnFailureListener());
    }


    private OnSuccessListener<Location> getOnSuccessListener() {
        return new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if(location != null) {
                    try {
                        _coordinateLatitudeTextField.setText(String.format(Locale.GERMAN, "%f", location.getLatitude()));
                        _coordinateLongitudeTextField.setText(String.format(Locale.GERMAN, "%f", location.getLongitude()));
                    } catch (Exception ex) {
                        _coordinateLatitudeTextField.setText(ERROR);
                        _coordinateLongitudeTextField.setText(ERROR);
                    }
                }
            }
        };
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        _sensorManager.unregisterListener(this);
        getApplicationContext().unregisterReceiver(_batteryLevelReceiver);

    }

    private void SetupOrientationService() {
        _sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        _orientationSensor = _sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        _sensorManager.registerListener(this, _orientationSensor, 10000);
    }

    private void SetupLocationService() {
        _fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                    REQUEST_CODE_CALLBACK_CODE);
            return; //fused location client will be setup during successful permission callback
        }
        ConfigureFusedLocationClient();
    }

    @SuppressLint("MissingPermission") //Handled in code
    private void ConfigureFusedLocationClient() {
        _fusedLocationRequest = new LocationRequest();
        _fusedLocationRequest.setInterval(_currentCycle);
        _fusedLocationRequest.setFastestInterval(_currentCycle);
        _fusedLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationCallback fusedLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        _coordinateLatitudeTextField.setText(String.format(Locale.GERMAN, "%f", location.getLatitude()));
                        _coordinateLongitudeTextField.setText(String.format(Locale.GERMAN, "%f", location.getLongitude()));
                    }
                }
            }
        };

        _fusedLocationClient.requestLocationUpdates(_fusedLocationRequest, fusedLocationCallback, null);
    }



    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_CALLBACK_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    ConfigureFusedLocationClient(); //configure location client
                } else {
                    finishAndRemoveTask(); //kill app, the user denied using location data
                }
                return;
            }
        }
    }

    private void SetupViews()
    {
        _coordinateLatitudeTextField = findViewById(R.id.coordinateLatitudeTextField);
        _coordinateLongitudeTextField = findViewById(R.id.coordinateLongitudeTextField);
        _coordinateUpdateButton = findViewById(R.id.coordinateUpdateButton);

        _orientationXTextField = findViewById(R.id.orientationXTextField);
        _orientationYTextField = findViewById(R.id.orientationYTextField);
        _orientationZTextField = findViewById(R.id.orientationZTextField);

        _chargingTextField = findViewById(R.id.isChargingTextField);
        _scantimeTextField = findViewById(R.id.scantimeTextField);
    }

    private OnFailureListener getOnFailureListener() {
        return new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                _coordinateLatitudeTextField.setText(ERROR);
                _coordinateLongitudeTextField.setText(ERROR);
            }
        };
    }



    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            try {
                float[] rotationMatrix = new float[16]; //x,y,z,w
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

                float[] orientations = new float[3]; //z,x,y
                SensorManager.getOrientation(rotationMatrix, orientations);

                _orientationZTextField.setText(String.format(Locale.GERMAN, "%f", orientations[0]));
                _orientationXTextField.setText(String.format(Locale.GERMAN, "%f", orientations[1]));
                _orientationYTextField.setText(String.format(Locale.GERMAN, "%f", orientations[2]));
            } catch (Exception ex) {
                _orientationZTextField.setText(ERROR);
                _orientationXTextField.setText(ERROR);
                _orientationYTextField.setText(ERROR);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}

