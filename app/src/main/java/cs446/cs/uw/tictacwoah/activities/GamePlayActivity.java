package cs446.cs.uw.tictacwoah.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.util.LinkedList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import cs446.cs.uw.tictacwoah.R;
import cs446.cs.uw.tictacwoah.activityModels.GamePlayModel;
import cs446.cs.uw.tictacwoah.models.Board;
import cs446.cs.uw.tictacwoah.models.Piece;
import cs446.cs.uw.tictacwoah.views.BoardView;
import cs446.cs.uw.tictacwoah.views.PieceView;
import cs446.cs.uw.tictacwoah.views.TurnIndicator;

public class GamePlayActivity extends AppCompatActivity implements Observer{

    // margin between TurnIndicators
    private final int marginTI = TurnIndicator.WIDTH;

    private GamePlayModel model;
    private Piece lastPlacedPiece;
    private Integer curPlayer;

    private RelativeLayout rootLayout;
    private BoardView boardView;
    private Button restartButton;

    private float widthOfSquare;
    private PieceView[][][] boardPieces;
    private List<TurnIndicator> turnIndicators;

    // for dragging pieces
    private float origX, origY, dX, dY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gameplay);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        model = new GamePlayModel(getIntent(), getApplicationContext());
        model.addObserver(this);
        lastPlacedPiece = null;
        curPlayer = null;

        rootLayout = findViewById(R.id.thisisalayout);

        int marginTop = marginTI * 2 + TurnIndicator.WIDTH;
        boardView = new BoardView(this, marginTop);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        boardView.setLayoutParams(layoutParams);
        rootLayout.addView(boardView);

        restartButton = findViewById(R.id.restart_button);
        restartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                newGame();
            }
        });

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        widthOfSquare = displayMetrics.widthPixels / 3;

        float heightOfScreen = displayMetrics.heightPixels;
        createDraggablePieces(heightOfScreen);

        boardPieces = new PieceView[PieceView.SIZES.length][Board.boardSize][Board.boardSize];
        turnIndicators = new LinkedList<>();
        inflateTurnIndicators();
        changeTurn();
    }

    private void createDraggablePieces(float heightOfScreen){
        // For each size, I create 2 pieces,
        // so when user drags a piece, there will still be another piece on the UI
        for (int j = 0; j < 2; ++j) {
            for (int i = 0; i < PieceView.SIZES.length; ++i) {
                float offset = (widthOfSquare - PieceView.SIZES[i]) / 2;
                float x = widthOfSquare * i + offset;
                float y = heightOfScreen - widthOfSquare + offset;
                PieceView view = PieceView.getPieceView(this, (int)x, (int)y, PieceView.SIZES[i], PieceView.COLORS[model.getMyPlayerId()], PieceView.TRIANGLE);
                rootLayout.addView(view);

                final int sizeId = i;
                view.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View view, MotionEvent event) {
                        int curAction = event.getActionMasked();
                        Integer rowId;
                        Integer colId;
                        switch (curAction) {
                            case MotionEvent.ACTION_DOWN:
                                origX = view.getX();
                                origY = view.getY();
                                // view.getX() refers to the top-left point of the view
                                // while event.getRawX() is the point where the cursor is
                                dX = view.getX() - event.getRawX();
                                dY = view.getY() - event.getRawY();
                                break;

                            case MotionEvent.ACTION_MOVE:
                                view.setY(event.getRawY() + dY);
                                view.setX(event.getRawX() + dX);
                                break;

                            case MotionEvent.ACTION_UP:
                                rowId = boardView.getRowId(getCenterXCoordinate(view));
                                colId = boardView.getColId(getCenterYCoordinate(view));

                                // the view was not on the board when the user finished dragging
                                if (rowId == null || colId == null){
                                    break;
                                }

                                // The piece is dragged onto the board, but it's not the user's turn
                                if (!model.isMyTurn()){
                                    Toast toast = Toast.makeText(getApplicationContext(), "It's not your turn", Toast.LENGTH_SHORT);
                                    toast.show();
                                    break;
                                }

                                if (!model.placePiece(new Piece(model.getMyPlayerId(), sizeId, rowId, colId))){
                                    Toast toast = Toast.makeText(getApplicationContext(), "This space is occupied.", Toast.LENGTH_SHORT);
                                    toast.show();
                                }

                                break;
                            default:
                                return false;
                        }
                        // we need to reset the position of the draggable piece
                        // when user finished dragging
                        if (curAction == MotionEvent.ACTION_UP){
                            view.setX(origX);
                            view.setY(origY);
                        }
                        return true;
                    }
                });
            }
        }
    }

    private float getCenterXCoordinate (View view) {
        return view.getX() + view.getWidth() / 2;
    }

    private float getCenterYCoordinate (View view) {
        return view.getY() + view.getHeight() / 2;
    }

//    @Override
//    protected void onResume(){
//        super.onResume();
//    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        model.closeBltConn();
    }

    private void newGame() {
        restartButton.setVisibility(View.GONE);
        for (int i = 0; i < boardPieces.length; ++i){
            for (int j = 0; j < boardPieces[i].length; ++j){
                for (int k = 0; k < boardPieces[i][j].length; ++k){
                    if (boardPieces[i][j][k] != null){
                        rootLayout.removeView(boardPieces[i][j][k]);
                        boardPieces[i][j][k] = null;
                    }
                }
            }
        }
        model.reset();
    }

    @Override
    public void update(Observable observable, Object object) {

        // when number of players increase
        inflateTurnIndicators();

        // because curPlay may have not been initialized (null)
        // so I invoke equals() on model.getCurPlayer()
        if (!model.getCurPlayer().equals(curPlayer)){
            changeTurn();
        }

        Piece piece = model.getLastPlacedPiece();
        if (piece != null && lastPlacedPiece != piece){
            drawPieceOnBoard(piece);
            lastPlacedPiece = piece;
        }

        // check if game is over and show the winning pattern
        if (model.isGameOver()){
            turnIndicators.get(curPlayer).stopAnimation();
            showWinningPattern(model.getWinningPattern());
            showWinOrLoseMessage(model.getWinningPattern()[0].getId());
            restartButton.setVisibility(View.VISIBLE);
        }
    }

    private void inflateTurnIndicators(){
        while (turnIndicators.size() < model.getNumPlayers()){
            int index = turnIndicators.size();  // the index of the TurnIndicator being added
            int x = marginTI * (index + 1) + TurnIndicator.WIDTH * index;
            int y = marginTI;
            int color = PieceView.COLORS[index];
            TurnIndicator turnIndicator = new TurnIndicator(this, x, y, color);
            turnIndicators.add(turnIndicator);
            rootLayout.addView(turnIndicator);
        }
    }

    private void changeTurn(){
        if (curPlayer != null){
            // stop the animation because the turn changes
            turnIndicators.get(curPlayer).stopAnimation();
        }
        // assign value but not reference
        // if we assign reference here,
        // curPlayer in this Activity will be updated when we update curPlayer in its model
        curPlayer = model.getCurPlayer().intValue();
        turnIndicators.get(curPlayer).startAnimation();
        TurnIndicator TI = turnIndicators.get(curPlayer);
        Log.d("TI", Double.toString(TI.getX()) + " " + Double.toString(TI.getY()) + " " + Integer.toString(TI.getWidth()) + " " + Integer.toString(TI.getHeight()));
    }

    private void drawPieceOnBoard(Piece piece){
        int sizeId = piece.getSizeId();
        int rowId = piece.getRowId();
        int colId = piece.getColId();
        int cellWidth = boardView.getCellWidth();

        float offset = (cellWidth - PieceView.SIZES[sizeId]) / 2;
        float x = cellWidth * rowId + offset;
        float y = boardView.MARGIN_TOP + cellWidth * colId + offset;

        PieceView view = PieceView.getPieceView(this, (int)x, (int)y, PieceView.SIZES[sizeId], PieceView.COLORS[piece.getId()], PieceView.TRIANGLE);
        rootLayout.addView(view);
        boardPieces[sizeId][rowId][colId] = view;
    }

    private void showWinningPattern(Piece[] pieces){
        for (Piece p : pieces){
            boardPieces[p.getSizeId()][p.getRowId()][p.getColId()].startAnimation();
        }
    }

    public void showWinOrLoseMessage(Integer winnerId){
        String msg = (winnerId.equals(model.getMyPlayerId())) ? "You win. " : "You lose. ";
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }
}