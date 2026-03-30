package com.example.scanner;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView txtStatus;
    private Button btnScan;
    private Button btnScanDocument;
    private WebSocketClient webSocketClient;

    // Pour la photo
    private Uri photoUri;
    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && photoUri != null) {
                    processImageForOCR(photoUri);
                } else {
                    txtStatus.setText("📷 Annulé");
                }
            });

    private final ActivityResultLauncher<ScanOptions> scannerLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    String code = result.getContents();
                    txtStatus.setText("📦 " + code);
                    Log.d("SCANNER", "Code scanné: " + code);
                    if (webSocketClient != null && webSocketClient.isOpen()) {
                        webSocketClient.send(code);
                    } else {
                        txtStatus.setText("⚠️ Serveur non connecté");
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtStatus = findViewById(R.id.txtStatus);
        btnScan = findViewById(R.id.btnScan);
        btnScanDocument = findViewById(R.id.btnScanDocument);

        connectWebSocket();

        btnScan.setOnClickListener(v -> startScan());
        btnScanDocument.setOnClickListener(v -> openCameraForDocument());
    }

    private void connectWebSocket() {
        try {
            URI uri = new URI("ws://192.168.43.13:3001"); // remplacez par l'IP de votre serveur
            webSocketClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    runOnUiThread(() -> txtStatus.setText("✅ Connecté"));
                    Log.d("SCANNER", "WebSocket ouvert");
                }

                @Override
                public void onMessage(String message) {
                    Log.d("SCANNER", "Message reçu: " + message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    runOnUiThread(() -> txtStatus.setText("❌ Déconnecté"));
                }

                @Override
                public void onError(Exception ex) {
                    runOnUiThread(() -> txtStatus.setText("⚠️ Erreur connexion"));
                    Log.e("SCANNER", "Erreur WebSocket", ex);
                }
            };
            webSocketClient.connect();
        } catch (Exception e) {
            Log.e("SCANNER", "Erreur connexion", e);
        }
    }

    private void startScan() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scanner un code-barres");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        scannerLauncher.launch(options);
    }

    private void openCameraForDocument() {
        File photoFile = null;
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(new Date());
            String imageFileName = "DOC_" + timeStamp + "_";
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            photoFile = File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (IOException e) {
            Log.e("SCANNER", "Erreur création fichier image", e);
            txtStatus.setText("❌ Erreur création fichier");
            return;
        }

        if (photoFile != null) {
            photoUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", photoFile);
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            cameraLauncher.launch(takePictureIntent);
        } else {
            txtStatus.setText("❌ Erreur création fichier");
        }
    }

    private void processImageForOCR(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            byte[] imageBytes = getBytes(inputStream);
            String base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT);

            if (webSocketClient != null && webSocketClient.isOpen()) {
                String message = "OCR:" + base64Image;
                webSocketClient.send(message);
                txtStatus.setText("📄 Analyse OCR en cours...");
                Log.d("SCANNER", "Image envoyée pour OCR, taille: " + base64Image.length());
            } else {
                txtStatus.setText("⚠️ Serveur non connecté");
            }
        } catch (Exception e) {
            Log.e("SCANNER", "Erreur lecture image", e);
            txtStatus.setText("❌ Erreur lecture image");
        }
    }

    private byte[] getBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webSocketClient != null) {
            webSocketClient.close();
        }
    }
}