package com.example.myapplication;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.CallLog;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Telephony;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private static final String[] CONDITIONAL_PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_MEDIA_IMAGES
    };

    private TextView statusText;
    private Button extractButton;
    private Button extractPhotosButton;
    private Button requestPermissionsButton;
    private Button recheckButton;
    private Button shareButton;
    private Button sharePhotosButton;
    private String lastExportedFilePath;
    private String lastPhotosZipPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initializeViews();
        checkPermissions();
    }

    private void initializeViews() {
        statusText = findViewById(R.id.statusText);
        extractButton = findViewById(R.id.extractButton);
        extractPhotosButton = findViewById(R.id.extractPhotosButton);
        requestPermissionsButton = findViewById(R.id.requestPermissionsButton);
        recheckButton = findViewById(R.id.recheckButton);
        shareButton = findViewById(R.id.shareButton);
        sharePhotosButton = findViewById(R.id.sharePhotosButton);

        requestPermissionsButton.setOnClickListener(v -> requestPermissions());
        recheckButton.setOnClickListener(v -> {
            Toast.makeText(this, "Rechecking permissions...", Toast.LENGTH_SHORT).show();
            checkPermissions();
        });
        extractButton.setOnClickListener(v -> extractData());
        extractPhotosButton.setOnClickListener(v -> extractPhotosData());
        shareButton.setOnClickListener(v -> shareExportedFile());
        sharePhotosButton.setOnClickListener(v -> sharePhotosZip());

        shareButton.setVisibility(View.GONE);
        sharePhotosButton.setVisibility(View.GONE);
        extractPhotosButton.setEnabled(false);
    }

    private void checkPermissions() {
        boolean allPermissionsGranted = true;
        StringBuilder missingPermissions = new StringBuilder();

        // Check required permissions
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                missingPermissions.append(permission).append("\n");
            }
        }

        // Check storage permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses READ_MEDIA_IMAGES
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                missingPermissions.append(Manifest.permission.READ_MEDIA_IMAGES).append("\n");
            }
        } else {
            // Older Android versions use READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                missingPermissions.append(Manifest.permission.READ_EXTERNAL_STORAGE).append("\n");
            }
        }

        if (allPermissionsGranted) {
            statusText.setText("✅ All permissions granted! Ready to extract data.");
            extractButton.setEnabled(true);
            extractPhotosButton.setEnabled(true);
            requestPermissionsButton.setVisibility(View.GONE);
        } else {
            statusText.setText("❌ Missing permissions:\n" + missingPermissions.toString() + "\nPlease grant all permissions to continue.");
            extractButton.setEnabled(false);
            extractPhotosButton.setEnabled(false);
            requestPermissionsButton.setVisibility(View.VISIBLE);
        }
    }

    private void requestPermissions() {
        // Create permissions list based on Android version
        String[] permissionsToRequest;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ - use READ_MEDIA_IMAGES
            permissionsToRequest = new String[]{
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        } else {
            // Older Android - use READ_EXTERNAL_STORAGE
            permissionsToRequest = new String[]{
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }

        ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Count granted permissions
            int grantedCount = 0;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    grantedCount++;
                }
            }

            Toast.makeText(this, grantedCount + " out of " + grantResults.length + " permissions granted", Toast.LENGTH_SHORT).show();

            // Always recheck permissions after the request
            checkPermissions();
        }
    }

    private void extractData() {
        statusText.setText("Extracting data...");

        new Thread(() -> {
            try {
                JSONObject exportData = new JSONObject();

                // Extract device info
                runOnUiThread(() -> statusText.setText("Extracting device info..."));
                JSONObject deviceInfo = extractDeviceInfo();
                exportData.put("device_info", deviceInfo);

                // Extract contacts
                runOnUiThread(() -> statusText.setText("Extracting contacts..."));
                JSONArray contacts = extractContacts();
                exportData.put("contacts", contacts);

                // Extract SMS
                runOnUiThread(() -> statusText.setText("Extracting SMS messages..."));
                JSONArray smsMessages = extractSMS();
                exportData.put("sms_messages", smsMessages);

                // Extract call logs
                runOnUiThread(() -> statusText.setText("Extracting call logs..."));
                JSONArray callLogs = extractCallLogs();
                exportData.put("call_logs", callLogs);

                // Extract calendar events
                runOnUiThread(() -> statusText.setText("Extracting calendar events..."));
                JSONArray calendarEvents = extractCalendarEvents();
                exportData.put("calendar_events", calendarEvents);

                // Extract photos metadata
                runOnUiThread(() -> statusText.setText("Extracting photos metadata..."));
                JSONArray photosMetadata = extractPhotosMetadata();
                exportData.put("photos_metadata", photosMetadata);

                // Extract location history
                runOnUiThread(() -> statusText.setText("Extracting location data..."));
                JSONArray locationHistory = extractLocationHistory();
                exportData.put("location_history", locationHistory);

                // Extract installed apps
                runOnUiThread(() -> statusText.setText("Extracting installed apps..."));
                JSONArray installedApps = extractInstalledApps();
                exportData.put("installed_apps", installedApps);

                // Save to file
                runOnUiThread(() -> statusText.setText("Saving data..."));
                String fileName = saveDataToFile(exportData);
                lastExportedFilePath = fileName;

                runOnUiThread(() -> {
                    statusText.setText("Data extracted successfully!\nSaved to: " + fileName);
                    shareButton.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "Data exported! Use Share button to send to computer.", Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setText("Error extracting data: " + e.getMessage());
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void extractPhotosData() {
        statusText.setText("Extracting photos and creating ZIP archive...");

        new Thread(() -> {
            try {
                // Create temporary photos export folder
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File externalDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                if (externalDir == null) {
                    externalDir = getFilesDir();
                }
                File tempPhotosFolder = new File(externalDir, "temp_photos_" + timestamp);
                if (!tempPhotosFolder.exists()) {
                    tempPhotosFolder.mkdirs();
                }

                JSONObject exportData = new JSONObject();
                JSONArray photosArray = new JSONArray();

                ContentResolver contentResolver = getContentResolver();
                String[] projection = {
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.DATA,
                        MediaStore.Images.Media.SIZE,
                        MediaStore.Images.Media.DATE_TAKEN,
                        MediaStore.Images.Media.DATE_ADDED,
                        MediaStore.Images.Media.MIME_TYPE,
                        MediaStore.Images.Media.WIDTH,
                        MediaStore.Images.Media.HEIGHT
                };

                Cursor cursor = contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        null,
                        null,
                        MediaStore.Images.Media.DATE_TAKEN + " DESC"
                );

                int photoCount = 0;
                if (cursor != null) {
                    runOnUiThread(() -> statusText.setText("Found " + cursor.getCount() + " photos. Starting extraction..."));

                    while (cursor.moveToNext()) {
                        try {
                            photoCount++;
                            final int currentPhoto = photoCount;

                            JSONObject photo = new JSONObject();

                            int idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID);
                            int nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                            int dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                            int sizeIndex = cursor.getColumnIndex(MediaStore.Images.Media.SIZE);
                            int dateTakenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN);
                            int dateAddedIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED);
                            int mimeIndex = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE);
                            int widthIndex = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH);
                            int heightIndex = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT);

                            String id = (idIndex >= 0) ? cursor.getString(idIndex) : "";
                            String name = (nameIndex >= 0) ? cursor.getString(nameIndex) : "";
                            String originalPath = (dataIndex >= 0) ? cursor.getString(dataIndex) : "";
                            long size = (sizeIndex >= 0) ? cursor.getLong(sizeIndex) : 0;
                            long dateTaken = (dateTakenIndex >= 0) ? cursor.getLong(dateTakenIndex) : 0;
                            long dateAdded = (dateAddedIndex >= 0) ? cursor.getLong(dateAddedIndex) : 0;
                            String mimeType = (mimeIndex >= 0) ? cursor.getString(mimeIndex) : "";
                            int width = (widthIndex >= 0) ? cursor.getInt(widthIndex) : 0;
                            int height = (heightIndex >= 0) ? cursor.getInt(heightIndex) : 0;

                            // Copy the actual photo file to temp folder
                            String copiedFilePath = "";
                            if (originalPath != null && !originalPath.isEmpty()) {
                                File originalFile = new File(originalPath);
                                if (originalFile.exists()) {
                                    File copiedFile = new File(tempPhotosFolder, "photo_" + id + "_" + name);
                                    if (copyFile(originalFile, copiedFile)) {
                                        copiedFilePath = copiedFile.getName();
                                    }
                                }
                            }

                            photo.put("id", id != null ? id : "");
                            photo.put("name", name != null ? name : "");
                            photo.put("original_path", originalPath != null ? originalPath : "");
                            photo.put("copied_file_name", copiedFilePath);
                            photo.put("size_bytes", size);
                            photo.put("date_taken", dateTaken);
                            photo.put("date_added", dateAdded);
                            photo.put("formatted_date_taken", dateTaken > 0 ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(dateTaken)) : "");
                            photo.put("formatted_date_added", dateAdded > 0 ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(dateAdded * 1000)) : "");
                            photo.put("mime_type", mimeType != null ? mimeType : "");
                            photo.put("width", width);
                            photo.put("height", height);

                            photosArray.put(photo);

                            // Update UI every 10 photos
                            if (currentPhoto % 10 == 0) {
                                runOnUiThread(() -> statusText.setText("Processed " + currentPhoto + " photos..."));
                            }

                        } catch (Exception e) {
                            // Skip this photo if there's an error
                            continue;
                        }
                    }
                    cursor.close();
                }

                exportData.put("photos", photosArray);
                exportData.put("total_photos", photoCount);
                exportData.put("export_timestamp", new Date().toString());

                // Save metadata JSON to temp folder
                String metadataFileName = "photos_metadata_" + timestamp + ".json";
                File metadataFile = new File(tempPhotosFolder, metadataFileName);
                FileWriter writer = new FileWriter(metadataFile);
                writer.write(exportData.toString(2));
                writer.close();

                // Create ZIP file
                runOnUiThread(() -> statusText.setText("Creating ZIP archive..."));
                String zipFileName = "photos_export_" + timestamp + ".zip";
                File zipFile = new File(externalDir, zipFileName);
                createZipFile(tempPhotosFolder, zipFile);

                // Clean up temp folder
                deleteFolder(tempPhotosFolder);

                lastPhotosZipPath = zipFile.getAbsolutePath();

                // Create final variables for lambda
                final int finalPhotoCount = photoCount;
                final String finalZipName = zipFile.getName();

                runOnUiThread(() -> {
                    statusText.setText("Photos ZIP created successfully!\n" + finalPhotoCount + " photos archived in: " + finalZipName);
                    sharePhotosButton.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "Photos ZIP exported! " + finalPhotoCount + " photos with metadata.", Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setText("Error extracting photos: " + e.getMessage());
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private boolean copyFile(File source, File destination) {
        try {
            FileInputStream inStream = new FileInputStream(source);
            FileOutputStream outStream = new FileOutputStream(destination);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inStream.read(buffer)) > 0) {
                outStream.write(buffer, 0, length);
            }

            inStream.close();
            outStream.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private JSONObject extractDeviceInfo() throws JSONException {
        JSONObject deviceInfo = new JSONObject();

        deviceInfo.put("device_model", Build.MODEL);
        deviceInfo.put("device_manufacturer", Build.MANUFACTURER);
        deviceInfo.put("device_brand", Build.BRAND);
        deviceInfo.put("android_version", Build.VERSION.RELEASE);
        deviceInfo.put("api_level", Build.VERSION.SDK_INT);
        deviceInfo.put("build_id", Build.ID);
        deviceInfo.put("hardware", Build.HARDWARE);
        deviceInfo.put("product", Build.PRODUCT);
        deviceInfo.put("board", Build.BOARD);
        deviceInfo.put("bootloader", Build.BOOTLOADER);
        deviceInfo.put("fingerprint", Build.FINGERPRINT);
        deviceInfo.put("extraction_timestamp", new Date().toString());

        return deviceInfo;
    }

    private JSONArray extractPhotosMetadata() throws JSONException {
        JSONArray photosArray = new JSONArray();
        ContentResolver contentResolver = getContentResolver();

        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
        };

        Cursor cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_TAKEN + " DESC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                try {
                    JSONObject photo = new JSONObject();

                    int idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID);
                    int nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                    int sizeIndex = cursor.getColumnIndex(MediaStore.Images.Media.SIZE);
                    int dateTakenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN);
                    int dateAddedIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED);
                    int mimeIndex = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE);
                    int widthIndex = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH);
                    int heightIndex = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT);

                    String id = (idIndex >= 0) ? cursor.getString(idIndex) : "";
                    String name = (nameIndex >= 0) ? cursor.getString(nameIndex) : "";
                    long size = (sizeIndex >= 0) ? cursor.getLong(sizeIndex) : 0;
                    long dateTaken = (dateTakenIndex >= 0) ? cursor.getLong(dateTakenIndex) : 0;
                    long dateAdded = (dateAddedIndex >= 0) ? cursor.getLong(dateAddedIndex) : 0;
                    String mimeType = (mimeIndex >= 0) ? cursor.getString(mimeIndex) : "";
                    int width = (widthIndex >= 0) ? cursor.getInt(widthIndex) : 0;
                    int height = (heightIndex >= 0) ? cursor.getInt(heightIndex) : 0;

                    photo.put("id", id != null ? id : "");
                    photo.put("name", name != null ? name : "");
                    photo.put("size_bytes", size);
                    photo.put("date_taken", dateTaken);
                    photo.put("date_added", dateAdded);
                    photo.put("formatted_date_taken", dateTaken > 0 ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(dateTaken)) : "");
                    photo.put("mime_type", mimeType != null ? mimeType : "");
                    photo.put("width", width);
                    photo.put("height", height);

                    photosArray.put(photo);
                } catch (Exception e) {
                    continue;
                }
            }
            cursor.close();
        }

        return photosArray;
    }

    private JSONArray extractLocationHistory() throws JSONException {
        JSONArray locationArray = new JSONArray();

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null) {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (lastKnownLocation != null) {
                        JSONObject location = new JSONObject();
                        location.put("latitude", lastKnownLocation.getLatitude());
                        location.put("longitude", lastKnownLocation.getLongitude());
                        location.put("accuracy", lastKnownLocation.getAccuracy());
                        location.put("timestamp", lastKnownLocation.getTime());
                        location.put("formatted_time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(lastKnownLocation.getTime())));
                        location.put("provider", lastKnownLocation.getProvider());
                        locationArray.put(location);
                    }
                }
            } catch (Exception e) {
                // Location not available
            }
        }

        return locationArray;
    }

    private JSONArray extractInstalledApps() throws JSONException {
        JSONArray appsArray = new JSONArray();
        PackageManager packageManager = getPackageManager();
        List<PackageInfo> packages = packageManager.getInstalledPackages(0);

        for (PackageInfo packageInfo : packages) {
            try {
                JSONObject app = new JSONObject();

                ApplicationInfo appInfo = packageInfo.applicationInfo;
                String appName = packageManager.getApplicationLabel(appInfo).toString();

                app.put("app_name", appName);
                app.put("package_name", packageInfo.packageName);
                app.put("version_name", packageInfo.versionName != null ? packageInfo.versionName : "");
                app.put("version_code", packageInfo.versionCode);
                app.put("install_time", packageInfo.firstInstallTime);
                app.put("update_time", packageInfo.lastUpdateTime);
                app.put("formatted_install_time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(packageInfo.firstInstallTime)));
                app.put("is_system_app", (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                app.put("enabled", appInfo.enabled);

                appsArray.put(app);
            } catch (Exception e) {
                continue;
            }
        }

        return appsArray;
    }

    private JSONArray extractContacts() throws JSONException {
        JSONArray contactsArray = new JSONArray();
        ContentResolver contentResolver = getContentResolver();

        Cursor cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null, null, null, null
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                try {
                    JSONObject contact = new JSONObject();

                    int contactIdIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID);
                    int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                    int phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    int typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE);

                    String contactId = (contactIdIndex >= 0) ? cursor.getString(contactIdIndex) : "";
                    String name = (nameIndex >= 0) ? cursor.getString(nameIndex) : "";
                    String phoneNumber = (phoneIndex >= 0) ? cursor.getString(phoneIndex) : "";
                    String phoneType = (typeIndex >= 0) ? cursor.getString(typeIndex) : "";

                    contact.put("contact_id", contactId != null ? contactId : "");
                    contact.put("name", name != null ? name : "");
                    contact.put("phone_number", phoneNumber != null ? phoneNumber : "");
                    contact.put("phone_type", phoneType != null ? phoneType : "");

                    String email = getContactEmail(contentResolver, contactId);
                    contact.put("email", email != null ? email : "");

                    contactsArray.put(contact);
                } catch (Exception e) {
                    continue;
                }
            }
            cursor.close();
        }

        return contactsArray;
    }

    private String getContactEmail(ContentResolver contentResolver, String contactId) {
        String email = "";
        if (contactId == null || contactId.isEmpty()) {
            return email;
        }

        Cursor emailCursor = null;
        try {
            emailCursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
                    new String[]{contactId},
                    null
            );

            if (emailCursor != null && emailCursor.moveToFirst()) {
                int emailIndex = emailCursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS);
                if (emailIndex >= 0) {
                    email = emailCursor.getString(emailIndex);
                }
            }
        } catch (Exception e) {
            // Return empty string if there's an error
        } finally {
            if (emailCursor != null) {
                emailCursor.close();
            }
        }

        return email != null ? email : "";
    }

    private JSONArray extractSMS() throws JSONException {
        JSONArray smsArray = new JSONArray();
        ContentResolver contentResolver = getContentResolver();

        Uri smsUri = Telephony.Sms.CONTENT_URI;
        String[] projection = {
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ
        };

        Cursor cursor = contentResolver.query(smsUri, projection, null, null, Telephony.Sms.DATE + " DESC");

        if (cursor != null) {
            while (cursor.moveToNext()) {
                try {
                    JSONObject sms = new JSONObject();

                    int idIndex = cursor.getColumnIndex(Telephony.Sms._ID);
                    int addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS);
                    int bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY);
                    int dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE);
                    int typeIndex = cursor.getColumnIndex(Telephony.Sms.TYPE);
                    int readIndex = cursor.getColumnIndex(Telephony.Sms.READ);

                    String id = (idIndex >= 0) ? cursor.getString(idIndex) : "";
                    String address = (addressIndex >= 0) ? cursor.getString(addressIndex) : "";
                    String body = (bodyIndex >= 0) ? cursor.getString(bodyIndex) : "";
                    long date = (dateIndex >= 0) ? cursor.getLong(dateIndex) : 0;
                    int type = (typeIndex >= 0) ? cursor.getInt(typeIndex) : 0;
                    int read = (readIndex >= 0) ? cursor.getInt(readIndex) : 0;

                    sms.put("id", id != null ? id : "");
                    sms.put("address", address != null ? address : "");
                    sms.put("body", body != null ? body : "");
                    sms.put("date", date);
                    sms.put("formatted_date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(date)));
                    sms.put("type", getMessageType(type));
                    sms.put("read", read == 1);

                    smsArray.put(sms);
                } catch (Exception e) {
                    continue;
                }
            }
            cursor.close();
        }

        return smsArray;
    }

    private String getMessageType(int type) {
        switch (type) {
            case Telephony.Sms.MESSAGE_TYPE_INBOX:
                return "RECEIVED";
            case Telephony.Sms.MESSAGE_TYPE_SENT:
                return "SENT";
            case Telephony.Sms.MESSAGE_TYPE_DRAFT:
                return "DRAFT";
            case Telephony.Sms.MESSAGE_TYPE_OUTBOX:
                return "OUTBOX";
            case Telephony.Sms.MESSAGE_TYPE_FAILED:
                return "FAILED";
            case Telephony.Sms.MESSAGE_TYPE_QUEUED:
                return "QUEUED";
            default:
                return "UNKNOWN";
        }
    }

    private JSONArray extractCallLogs() throws JSONException {
        JSONArray callLogsArray = new JSONArray();
        ContentResolver contentResolver = getContentResolver();

        String[] projection = {
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE
        };

        Cursor cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                CallLog.Calls.DATE + " DESC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                try {
                    JSONObject callLog = new JSONObject();

                    int idIndex = cursor.getColumnIndex(CallLog.Calls._ID);
                    int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
                    int nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
                    int dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE);
                    int durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION);
                    int typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE);

                    String id = (idIndex >= 0) ? cursor.getString(idIndex) : "";
                    String number = (numberIndex >= 0) ? cursor.getString(numberIndex) : "";
                    String name = (nameIndex >= 0) ? cursor.getString(nameIndex) : "";
                    long date = (dateIndex >= 0) ? cursor.getLong(dateIndex) : 0;
                    int duration = (durationIndex >= 0) ? cursor.getInt(durationIndex) : 0;
                    int type = (typeIndex >= 0) ? cursor.getInt(typeIndex) : 0;

                    callLog.put("id", id != null ? id : "");
                    callLog.put("number", number != null ? number : "");
                    callLog.put("name", name != null ? name : "Unknown");
                    callLog.put("date", date);
                    callLog.put("formatted_date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(date)));
                    callLog.put("duration_seconds", duration);
                    callLog.put("call_type", getCallType(type));

                    callLogsArray.put(callLog);
                } catch (Exception e) {
                    continue;
                }
            }
            cursor.close();
        }

        return callLogsArray;
    }

    private String getCallType(int type) {
        switch (type) {
            case CallLog.Calls.INCOMING_TYPE:
                return "INCOMING";
            case CallLog.Calls.OUTGOING_TYPE:
                return "OUTGOING";
            case CallLog.Calls.MISSED_TYPE:
                return "MISSED";
            case CallLog.Calls.VOICEMAIL_TYPE:
                return "VOICEMAIL";
            case CallLog.Calls.REJECTED_TYPE:
                return "REJECTED";
            case CallLog.Calls.BLOCKED_TYPE:
                return "BLOCKED";
            default:
                return "UNKNOWN";
        }
    }

    private JSONArray extractCalendarEvents() throws JSONException {
        JSONArray calendarArray = new JSONArray();
        ContentResolver contentResolver = getContentResolver();

        String[] projection = {
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.CALENDAR_DISPLAY_NAME
        };

        Cursor cursor = contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                null,
                null,
                CalendarContract.Events.DTSTART + " DESC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                try {
                    JSONObject event = new JSONObject();

                    int idIndex = cursor.getColumnIndex(CalendarContract.Events._ID);
                    int titleIndex = cursor.getColumnIndex(CalendarContract.Events.TITLE);
                    int descIndex = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION);
                    int startIndex = cursor.getColumnIndex(CalendarContract.Events.DTSTART);
                    int endIndex = cursor.getColumnIndex(CalendarContract.Events.DTEND);
                    int locationIndex = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION);
                    int calendarIndex = cursor.getColumnIndex(CalendarContract.Events.CALENDAR_DISPLAY_NAME);

                    String id = (idIndex >= 0) ? cursor.getString(idIndex) : "";
                    String title = (titleIndex >= 0) ? cursor.getString(titleIndex) : "";
                    String description = (descIndex >= 0) ? cursor.getString(descIndex) : "";
                    long startTime = (startIndex >= 0) ? cursor.getLong(startIndex) : 0;
                    long endTime = (endIndex >= 0) ? cursor.getLong(endIndex) : 0;
                    String location = (locationIndex >= 0) ? cursor.getString(locationIndex) : "";
                    String calendar = (calendarIndex >= 0) ? cursor.getString(calendarIndex) : "";

                    event.put("id", id != null ? id : "");
                    event.put("title", title != null ? title : "");
                    event.put("description", description != null ? description : "");
                    event.put("start_time", startTime);
                    event.put("end_time", endTime);
                    event.put("formatted_start", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(startTime)));
                    event.put("formatted_end", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(endTime)));
                    event.put("location", location != null ? location : "");
                    event.put("calendar_name", calendar != null ? calendar : "");

                    calendarArray.put(event);
                } catch (Exception e) {
                    continue;
                }
            }
            cursor.close();
        }

        return calendarArray;
    }

    private String saveDataToFile(JSONObject data) throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "data_export_" + timestamp + ".json";

        File externalDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (externalDir == null) {
            externalDir = getFilesDir();
        }
        File file = new File(externalDir, fileName);

        FileWriter writer = new FileWriter(file);
        try {
            writer.write(data.toString(2));
        } catch (JSONException e) {
            writer.write(data.toString());
        }
        writer.close();

        return file.getAbsolutePath();
    }

    private void shareExportedFile() {
        if (lastExportedFilePath == null || lastExportedFilePath.isEmpty()) {
            Toast.makeText(this, "No file to share. Please extract data first.", Toast.LENGTH_SHORT).show();
            return;
        }

        File file = new File(lastExportedFilePath);
        if (!file.exists()) {
            Toast.makeText(this, "Exported file not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Complete Phone Data Export");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Complete phone data export from " + new Date().toString());
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share exported data"));

        } catch (Exception e) {
            Toast.makeText(this, "Error sharing file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void sharePhotosZip() {
        if (lastPhotosZipPath == null || lastPhotosZipPath.isEmpty()) {
            Toast.makeText(this, "No photos ZIP to share. Please extract photos first.", Toast.LENGTH_SHORT).show();
            return;
        }

        File zipFile = new File(lastPhotosZipPath);
        if (!zipFile.exists()) {
            Toast.makeText(this, "Photos ZIP file not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    zipFile
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/zip");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Complete Photos Archive");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Complete photos archive with metadata from " + new Date().toString());
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share Photos ZIP"));

        } catch (Exception e) {
            Toast.makeText(this, "Error sharing photos ZIP: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void createZipFile(File sourceFolder, File zipFile) throws IOException {
        FileOutputStream fos = new FileOutputStream(zipFile);
        ZipOutputStream zos = new ZipOutputStream(fos);

        File[] files = sourceFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    addFileToZip(zos, file, file.getName());
                }
            }
        }

        zos.close();
        fos.close();
    }

    private void addFileToZip(ZipOutputStream zos, File file, String entryName) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        ZipEntry zipEntry = new ZipEntry(entryName);
        zos.putNextEntry(zipEntry);

        byte[] buffer = new byte[1024];
        int length;
        while ((length = fis.read(buffer)) > 0) {
            zos.write(buffer, 0, length);
        }

        zos.closeEntry();
        fis.close();
    }

    private void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteFolder(file);
                } else {
                    file.delete();
                }
            }
        }
        folder.delete();
    }
}