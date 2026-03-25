package com.example.scanner;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

public class MainActivity extends AppCompatActivity {

    private TextView txtStatus;
    private Button btnScan;
    private WebSocketClient webSocketClient;

    private final ActivityResultLauncher<ScanOptions> scannerLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    String code = result.getContents();
                    txtStatus.setText("📦 " + code);
                    Log.d("SCANNER", "Code scanné: " + code);

                    if (webSocketClient != null && webSocketClient.isOpen()) {
                        Log.d("SCANNER", "Envoi du code au serveur");
                        webSocketClient.send(code);
                    } else {
                        Log.e("SCANNER", "WebSocket non ouvert, code non envoyé");
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

        connectWebSocket();

        btnScan.setOnClickListener(v -> startScan());
    }

    private void connectWebSocket() {
        try {
            URI uri = new URI("ws://10.119.182.167:3001"); // ⚠️ Vérifie cette IP

            webSocketClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    runOnUiThread(() -> txtStatus.setText("✅ Connecté au serveur"));
                    Log.d("SCANNER", "WebSocket ouvert");
                }

                @Override
                public void onMessage(String message) {
                    Log.d("SCANNER", "Message reçu du serveur: " + message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    runOnUiThread(() -> txtStatus.setText("❌ Déconnecté"));
                    Log.d("SCANNER", "WebSocket fermé: " + code + " - " + reason);
                }

                @Override
                public void onError(Exception ex) {
                    runOnUiThread(() -> txtStatus.setText("⚠️ Erreur connexion"));
                    Log.e("SCANNER", "Erreur WebSocket", ex);
                }
            };

            webSocketClient.connect();
            Log.d("SCANNER", "Connexion WebSocket en cours...");

        } catch (Exception e) {
            Log.e("SCANNER", "Erreur de connexion", e);
            e.printStackTrace();
        }
    }

    private void startScan() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scanner un code-barres");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        scannerLauncher.launch(options);
    }
}