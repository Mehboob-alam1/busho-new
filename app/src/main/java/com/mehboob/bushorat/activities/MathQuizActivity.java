package com.mehboob.bushorat.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.mehboob.bushorat.R;
import com.mehboob.bushorat.helpers.FirebaseUtils;

import java.util.Random;

public class MathQuizActivity extends AppCompatActivity {

    private TextView tvQuestion, tvQuestionNo, tvScore;
    private EditText etAnswer;
    private Button btnSubmit;

    private final Random random = new Random();
    private int correctAnswer;
    private int questionIndex = 1;
    private int score = 0;
    private final int TOTAL_QUESTIONS = 10;
    private  String userId ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_math_quiz);
        userId= FirebaseAuth.getInstance().getCurrentUser().getUid();

        tvQuestion   = findViewById(R.id.tvQuestion);
        tvQuestionNo = findViewById(R.id.tvQuestionNo);
        tvScore      = findViewById(R.id.tvScore);
        etAnswer     = findViewById(R.id.etAnswer);
        btnSubmit    = findViewById(R.id.btnSubmit);

        loadNextQuestion();

        btnSubmit.setOnClickListener(v -> submitAnswer());
    }

    private void loadNextQuestion() {
        if (questionIndex > TOTAL_QUESTIONS) {
            endGame();
            return;
        }

        int a = random.nextInt(10) + 1;  // 1‑10
        int b = random.nextInt(10) + 1;
        int op = random.nextInt(3);      // 0:+ 1:- 2:×

        switch (op) {
            case 0:
                correctAnswer = a + b;
                tvQuestion.setText(a + " + " + b + " = ?");
                break;
            case 1:
                correctAnswer = a - b;
                tvQuestion.setText(a + " - " + b + " = ?");
                break;
            default:
                correctAnswer = a * b;
                tvQuestion.setText(a + " × " + b + " = ?");
        }

        tvQuestionNo.setText("Question " + questionIndex + "/" + TOTAL_QUESTIONS);
        etAnswer.setText("");
    }

    private void submitAnswer() {
        String ansText = etAnswer.getText().toString().trim();
        if (TextUtils.isEmpty(ansText)) {
            Toast.makeText(this, "Enter an answer", Toast.LENGTH_SHORT).show();
            return;
        }

        int userAns = Integer.parseInt(ansText);
        if (userAns == correctAnswer) {
            score += 5;
            Toast.makeText(this, "Correct! +5 pts", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Wrong. 0 pts", Toast.LENGTH_SHORT).show();
        }
        tvScore.setText("Score " + score);
        questionIndex++;
        loadNextQuestion();
    }

    private void endGame() {
        FirebaseUtils.updatePoints(userId, score);   // 🔥 add to shared points
        Toast.makeText(this, "Quiz finished! +" + score + " pts", Toast.LENGTH_LONG).show();
        finish(); // close activity
    }
}
