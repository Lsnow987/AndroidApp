package com.example.myapplication;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
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
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS
    };

    private TextView statusText;
    private Button extractButton;
    private Button requestPermissionsButton;
    private Button shareButton;
    private String lastExportedFilePath;

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
        requestPermissionsButton = findViewById(R.id.requestPermissionsButton);
        shareButton = findViewById(R.id.shareButton);

        requestPermissionsButton.setOnClickListener(v -> requestPermissions());
        extractButton.setOnClickListener(v -> extractData());
        shareButton.setOnClickListener(v -> shareExportedFile());

        shareButton.setVisibility(View.GONE); // Hide until file is exported
    }

    private void checkPermissions() {
        boolean allPermissionsGranted = true;
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (allPermissionsGranted) {
            statusText.setText("Permissions granted. Ready to extract data.");
            extractButton.setEnabled(true);
            requestPermissionsButton.setVisibility(View.GONE);
        } else {
            statusText.setText("Permissions required. Please grant permissions to continue.");
            extractButton.setEnabled(false);
            requestPermissionsButton.setVisibility(View.VISIBLE);
        }
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show();
                checkPermissions();
            } else {
                Toast.makeText(this, "Permissions are required for data extraction", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void extractData() {
        statusText.setText("Extracting data...");

        new Thread(() -> {
            try {
                JSONObject exportData = new JSONObject();

                // Extract contacts
                runOnUiThread(() -> statusText.setText("Extracting contacts..."));
                JSONArray contacts = extractContacts();
                exportData.put("contacts", contacts);

                // Extract SMS
                runOnUiThread(() -> statusText.setText("Extracting SMS messages..."));
                JSONArray smsMessages = extractSMS();
                exportData.put("sms_messages", smsMessages);

                // Save to file
                runOnUiThread(() -> statusText.setText("Saving data..."));
                String fileName = saveDataToFile(exportData);
                lastExportedFilePath = fileName; // Store for sharing

                runOnUiThread(() -> {
                    statusText.setText("Data extracted successfully!\nSaved to: " + fileName);
                    shareButton.setVisibility(View.VISIBLE); // Show share button
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

                    // Get email if available
                    String email = getContactEmail(contentResolver, contactId);
                    contact.put("email", email != null ? email : "");

                    contactsArray.put(contact);
                } catch (Exception e) {
                    // Skip this contact if there's an error
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
                    // Skip this SMS if there's an error
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

    private String saveDataToFile(JSONObject data) throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "data_export_" + timestamp + ".json";

        File externalDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (externalDir == null) {
            externalDir = getFilesDir(); // Fallback to internal storage
        }
        File file = new File(externalDir, fileName);

        FileWriter writer = new FileWriter(file);
        try {
            writer.write(data.toString(2)); // Pretty print with indentation
        } catch (JSONException e) {
            // Fallback to simple toString if pretty print fails
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
            // Create content URI using FileProvider
            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Exported Contacts and SMS Data");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Here's my exported contacts and SMS data from " + new Date().toString());
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share exported data"));

        } catch (Exception e) {
            Toast.makeText(this, "Error sharing file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}