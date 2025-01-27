/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2009,2011-2013,2015,2017-2019 Jeremy D Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * The maintainer of this program can be reached at jsettlers@nand.net
 **/
package soc.client;

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.TimerTask;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;


/**
 * Panel to display a resource trade offer (and counter-offer) from another player.
 * To show, update, or clear the trade offer, show {@link #setOffer(SOCTradeOffer)}.
 *<P>
 * This panel is written for use in {@link SOCHandPanel}, so its layout conventions are nonstandard.
 * To help determine {@link #getPreferredSize()}, call {@link #setAvailableSpace(int, int)} when known.
 *<P>
 * To set this panel's position or size, please use {@link #setBounds(int, int, int, int)},
 * because it is overridden to also update a "compact mode" flag for counter-offer layout.
 *
 * <H3>TODO:</H3>
 *<UL>
 * <LI> Consider separating offerpanel, messagepanel to 2 separate components
 *      that handpanel shows/hides/manages separately
 * <LI> Consider combine ShadowedBox, SpeechBalloon: They look the same except for that balloon point
 * <LI> Consider rework ShadowedBox, SpeechBalloon to have a custom-drawn Border
 *</UL>
 */
@SuppressWarnings("serial")
/*package*/ class TradeOfferPanel extends JPanel
{
    /** i18n text strings; will use same locale as SOCPlayerClient's string manager.
     *  @since 2.0.00 */
    private static final soc.util.SOCStringManager strings = soc.util.SOCStringManager.getClientManager();

    /**
     * Typical button height, for doLayouts. Not scaled.
     * @since 2.0.00
     */
    private static final int BUTTON_HEIGHT = 18;

    /**
     * Typical button width, for doLayouts. Not scaled.
     * @since 2.0.00
     */
    private static final int BUTTON_WIDTH = 55;

    /**
     * Height of a single-line text label in pixels,
     * including the auto-reject timer countdown when visible.
     * For convenience of other classes' layout calculations.
     * Not scaled by {@link SOCPlayerInterface#displayScale}.
     * @see OfferPanel#wantsRejectCountdown()
     * @since 1.2.00
     */
    public static final int LABEL_LINE_HEIGHT = 14;

    /**
     * Typical height of offer panel when visible. Includes {@link #OFFER_BUTTONS_ADDED_HEIGHT}
     * and speech balloon's protruding point, but not {@link #OFFER_COUNTER_HEIGHT}.
     * Doesn't include {@link #LABEL_LINE_HEIGHT} needed when
     * {@link OfferPanel#wantsRejectCountdown()}.
     *<P>
     * For convenience of other classes' layout calculations.
     * Actual height (buttons' y-positions + height) is set dynamically in {@link OfferPanel#doLayout()}.
     * Not scaled by {@link SOCPlayerInterface#displayScale}.
     * @since 1.1.08
     */
    public static final int OFFER_HEIGHT
        = SpeechBalloon.BALLOON_POINT_SIZE + 3
        + (2 * LABEL_LINE_HEIGHT + 4) + (SquaresPanel.HEIGHT + 5) + BUTTON_HEIGHT + 5
        + SpeechBalloon.SHADOW_SIZE;
        // same formula as OfferPanel.doLayout()

    /**
     * Additional height of offer (part of {@link #OFFER_HEIGHT})
     * when the "offer"/"accept"/"reject" buttons are showing.
     * That is, when not in counter-offer mode.
     * For convenience of other classes' layout calculations.
     * Based on calculations within OfferPanel.doLayout.
     *<P>
     * Not scaled by {@link SOCPlayerInterface#displayScale}.
     *<P>
     * Before v2.0.00 this field was {@code OFFER_BUTTONS_HEIGHT}.
     *
     * @since 1.1.08
     */
    public static final int OFFER_BUTTONS_ADDED_HEIGHT = BUTTON_HEIGHT + 5 + 2;
        // when counter-offer showing, squaresPanel moves up 2

    /**
     * Typical height of counter-offer panel, when visible.
     * For convenience of other classes' layout calculations.
     * Actual height of counter-offer (offerBox) is set dynamically in OfferPanel.doLayout.
     *<P>
     * If counter-offer is using compact mode, must subtract {@link #BUTTON_HEIGHT} + 2.
     *<P>
     * Not scaled by {@link SOCPlayerInterface#displayScale}.
     *
     * @since 1.1.08
     */
    public static final int OFFER_COUNTER_HEIGHT
        = 4 + ColorSquareLarger.HEIGHT_L + SquaresPanel.HEIGHT + 6 + BUTTON_HEIGHT + 7 + ShadowedBox.SHADOW_SIZE;
        // As calculated in OfferPanel.doLayout(); label's lineH = ColorSquareLarger.HEIGHT_L

    /**
     * For {@link #OFFER_MIN_WIDTH}, width from labels and squarepanel, including {@link SpeechBalloon#SHADOW_SIZE}.
     * Not scaled by {@link #displayScale}.
     * @since 2.0.00
     */
    private static final int OFFER_MIN_WIDTH_FROM_LABELS
        = (8 + OfferPanel.GIVES_MIN_WIDTH + 6 + SquaresPanel.WIDTH + 8) + SpeechBalloon.SHADOW_SIZE;

    /**
     * For {@link #OFFER_MIN_WIDTH}, width from 3 buttons, including {@link SpeechBalloon#SHADOW_SIZE}.
     * Not scaled by {@link #displayScale}.
     * @since 2.0.00
     */
    private static final int OFFER_MIN_WIDTH_FROM_BUTTONS
        = (2 * (5+5) + 3 * BUTTON_WIDTH) + SpeechBalloon.SHADOW_SIZE;

    /**
     * Offer panel minimum width for {@link OfferPanel#doLayout()}.<BR>
     * The larger of:
     *<UL>
     * <LI> Button widths: 3 buttons, with inset of 5 pixels from edge and buffer of 5 between buttons.
     *     ({@link #OFFER_MIN_WIDTH_FROM_BUTTONS})
     * <LI> Give/get widths: "Gives you/You get" labels ({@link OfferPanel#GIVES_MIN_WIDTH}), with
     *     inset of 8 pixels from left edge, 6 between label and SquaresPanel, 8 from right edge.
     *     ({@link #OFFER_MIN_WIDTH_FROM_LABELS})
     *</UL>
     * Width includes {@link SpeechBalloon#SHADOW_SIZE} along right edge.
     *<P>
     * If counter-offer is visible and in compact mode, use {@link #OFFER_COMPACT_MIN_WIDTH} instead.
     *<P>
     * Not scaled by {@link #displayScale}.
     *
     * @since 2.0.00
     */
    private static final int OFFER_MIN_WIDTH
        = Math.max(OFFER_MIN_WIDTH_FROM_BUTTONS, OFFER_MIN_WIDTH_FROM_LABELS);

    /**
     * Offer panel minimum width in counter-offer compact mode, which is less tall but wider.
     * Not scaled by {@link #displayScale}.
     * @see #OFFER_MIN_WIDTH
     * @since 2.0.00
     */
    private static final int OFFER_COMPACT_MIN_WIDTH
        = 2 + OfferPanel.GIVES_MIN_WIDTH + 6 + SquaresPanel.WIDTH + 2 + BUTTON_WIDTH + 2 + SpeechBalloon.SHADOW_SIZE;

    /**
     * Initial size, to avoid (0,0)-sized JPanel during parent panels' construction.
     * Not scaled by {@link #displayScale}.
     * @since 2.0.00
     */
    private static final Dimension INITIAL_SIZE = new Dimension(OFFER_MIN_WIDTH, OFFER_HEIGHT);

    protected static final int[] zero = new int[5];  // { 0, 0, 0, 0, 0 }

    static final String OFFER = "counter";
    static final String ACCEPT = "accept";
    static final String REJECT = "reject";
    static final String SEND = "send";
    static final String CLEAR = "clear";
    static final String CANCEL = "cancel";

    /** This panel's player number */
    private final int from;

    /**
     * True if {@link #from} is a robot player.
     * @since 1.2.00
     */
    private boolean isFromRobot;

    /** This TradeOfferPanel's parent hand panel, for action callbacks from buttons */
    private final SOCHandPanel hp;

    /** {@link #hp}'s parent player interface */
    private final SOCPlayerInterface pi;

    /**
     * For high-DPI displays, what scaling factor to use? Unscaled is 1.
     * @since 2.0.00
     */
    private final int displayScale;

    final OfferPanel offerPanel;

    /**
     * Available width and height in handpanel. Used for determining {@link #getPreferredSize()},
     * overall shape of which changes when a counter-offer needs to use {@link #counterCompactMode}.
     * Is 0 (unused) until {@link #setAvailableSpace(int, int)} is called.
     * @since 2.0.00
     */
    private int availableWidth, availableHeight;

    /**
     * If true, display counter-offer in a "compact mode" layout
     * because the panel's height is too short for the normal arrangement.
     * Buttons (width {@link #BUTTON_WIDTH}) will be to the right of colorsquares, not below them.
     * Calculated using {@link #OFFER_HEIGHT} + {@link #OFFER_COUNTER_HEIGHT}
     *     - {@link #OFFER_BUTTONS_ADDED_HEIGHT}.
     * Ignored unless {@link OfferPanel#counterOfferMode}.
     * @since 1.1.08
     */
    private boolean counterCompactMode;

    /**
     * If true, hide the original offer's balloon point
     * (see {@link SpeechBalloon#setBalloonPoint(boolean)})
     * when the counter-offer is visible.
     * @since 1.1.08
     */
    private boolean counterHidesBalloonPoint;

    /**
     * Creates a new TradeOfferPanel object.
     * @param hp  New TradeOfferPanel's parent hand panel, for action callbacks from trade buttons
     * @param from  {@code hp}'s player number
     */
    public TradeOfferPanel(SOCHandPanel hp, int from)
    {
        super(new BorderLayout());

        this.hp = hp;
        this.from = from;
        pi = hp.getPlayerInterface();
        displayScale = pi.displayScale;

        offerPanel = new OfferPanel();
        add(offerPanel, BorderLayout.CENTER);  // Is temporary, since this class has only OfferPanel now

        addPlayer();  // set isFromRobot, etc

        counterCompactMode = false;
        counterHidesBalloonPoint = false;

        // without these calls, parent JPanel layout is incomplete even when this panel overrides get*Size
        setSize(INITIAL_SIZE);
        setMinimumSize(INITIAL_SIZE);
        setPreferredSize(INITIAL_SIZE);  // will be updated when setAvailableSpace is called
    }

    /**
     * Set the size of the largest space available for this panel in our {@link SOCHandPanel}.
     * If space's size has changed since the last call, calls {@link #recalcPreferredSize()}.
     *
     * @param width  Available width
     * @param height  Available height
     * @since 2.0.00
     */
    public void setAvailableSpace(final int width, final int height)
    {
        if ((width == availableWidth) && (height == availableHeight))
            return;

        availableWidth = width;
        availableHeight = height;

        recalcPreferredSize();
    }

    /**
     * Recalculate our panel's {@link #getPreferredSize()}.
     * Useful when counter-offer is being shown or hidden, which might need a "compact mode".
     * with a different width than otherwise. So, also updates that flag if panel is showing a counter-offer.
     * Does not call {@link #invalidate()}, so call that afterwards if needed.
     *
     * @see #setAvailableSpace(int, int)
     * @since 2.0.00
     */
    public void recalcPreferredSize()
    {
        int prefW, prefH;

        prefW = OFFER_MIN_WIDTH * displayScale;
        final int labelWidthChg = offerPanel.calcLabelWidth() - (OfferPanel.GIVES_MIN_WIDTH * displayScale);
        if (labelWidthChg > 0)
            prefW = Math.max
                (OFFER_MIN_WIDTH_FROM_BUTTONS * displayScale,
                 OFFER_MIN_WIDTH_FROM_LABELS * displayScale + labelWidthChg);

        if (! offerPanel.counterOfferMode)
        {
            prefH = OFFER_HEIGHT * displayScale;
        } else {
            final boolean wasCompact = counterCompactMode;

            prefH = (OFFER_HEIGHT - OFFER_BUTTONS_ADDED_HEIGHT + OFFER_COUNTER_HEIGHT) * displayScale;
            if (availableHeight >= prefH)
            {
                counterCompactMode = false;
            } else {
                counterCompactMode = true;
                prefH -= ((BUTTON_HEIGHT + 2) * displayScale);
                prefW = OFFER_COMPACT_MIN_WIDTH * displayScale;
                if (labelWidthChg > 0)
                    prefW += labelWidthChg;
            }

            if (wasCompact != counterCompactMode)
                repaint();
        }

        if (! (offerPanel.counterOfferMode && counterCompactMode))
        {
            if (offerPanel.wantsRejectCountdown())
                prefH += (LABEL_LINE_HEIGHT * displayScale);
        } else {
            prefH -= (SpeechBalloon.BALLOON_POINT_SIZE * displayScale);
        }

        if ((availableWidth != 0) && (availableWidth < prefW))
            prefW = availableWidth;
        if ((availableHeight != 0) && (availableHeight < prefH))
            prefH = availableHeight;

        setPreferredSize(new Dimension(prefW, prefH));
    }

    /**
     * Panel to show a trade offer.
     * Contains both offer and counter-offer; see {@link #setCounterOfferVisible(boolean)}.
     * @see MessagePanel
     */
    /*package*/ class OfferPanel extends JPanel implements ActionListener
    {
        /**
         * Minimum width for "Gives you/You get" labels, for fallback if FontMetrics not available yet.
         * Not scaled by {@code displayScale}.
         * @since 2.0.00
         */
        static final int GIVES_MIN_WIDTH = 49;

        /**
         * Balloon JPanel to hold offer received.
         * Fill color is {@link TradeOfferPanel#insideBGColor}.
         * Has custom layout arranged in {@link #doLayout()}.
         * @see #counterOfferBox
         */
        final SpeechBalloon balloon;

        /** "Offered To" line 1 */
        final JLabel toWhom1;

        /** "Offered To" line 2 for wrapping; usually blank */
        final JLabel toWhom2;

        /**
         * Top row "Gives You:". Client player {@link SOCHandPanel} has "I Give" on this row.
         *<P>
         * Before v1.2.00 this label field was {@code giveLab}.
         * @see #givesYouLabWidth
         */
        final JLabel givesYouLab;

        /**
         * Bottom row "They Get:". Client player {@link SOCHandPanel} has "I Get" on this row.
         *<P>
         * Before v1.2.00 this label field was {@code getLab}.
         * @see #givesYouLabWidth
         */
        final JLabel theyGetLab;

        /**
         * Width in pixels of the text in the "Gives You:"/"They Get:" labels, whichever is wider,
         * from <tt>getFontMetrics({@link #givesYouLab}.getFont()).getWidth()</tt> and
         * same from {@link #theyGetLab}. 0 if unknown.
         * @since 2.0.00
         */
        private int givesYouLabWidth;

        /** Offer's resources; counter-offer is {@link #counterOfferSquares}. */
        final SquaresPanel squares;

        /** "Counter" button to show counter-offer panel */
        final JButton offerBut;

        /** Button to accept this other player's proposed trade */
        final JButton acceptBut;

        /** Button to reject this other player's proposed trade */
        final JButton rejectBut;

        /**
         * Counter-offer to send; a JPanel that groups counter-offer elements.
         * Has custom layout arranged in {@link #doLayout()}.
         *<P>
         * Before v2.0.00 this field was {@code offerBox}.
         *
         * @see #balloon
         */
        final ShadowedBox counterOfferBox;

        final JLabel counterOfferToWhom;

        /** Have we set prompt to include opponent name? Is set true by first call to {@link #update(SOCTradeOffer)}. */
        boolean counterOffer_playerInit = false;

        /** Counter-offer's resources; the main offer is {@link #squares}. */
        final SquaresPanel counterOfferSquares;

        /**
         * Counter-offer top row "They Get:". Same as main offer's bottom row.
         *<P>
         * Before v1.2.00 this label field was {@code giveLab2}.
         */
        final JLabel theyGetLab2;

        /**
         * Counter-offer bottom row "Gives You:". Same as main offer's top row.
         *<P>
         * Before v1.2.00 this label field was {@code getLab2}.
         */
        final JLabel givesYouLab2;

        /** Button to send counter-offer */
        final JButton sendBut;

        /** Button to clear counter-offer */
        final JButton clearBut;

        /** Button to cancel counter-offer and hide its panel */
        final JButton cancelBut;

        /** True if the current offer's "offered to" includes the client player. */
        boolean offered;

        /**
         * Auto-reject countdown timer text below offer panel, or {@code null}.
         * Used for bots only. Visible only if {@link #offered} and
         * {@link TradeOfferPanel#isFromRobot}. Initialized in {@link #addPlayer()}.
         * Visibility is updated in {@link #update(SOCTradeOffer)}.
         * If counter-offer panel is shown, this label is hidden and the countdown
         * is canceled because client player might take action on the offer.
         * When canceling the timer and hiding this label, should also call setText("").
         * @see #rejTimerTask
         * @since 1.2.00
         */
        private JLabel rejCountdownLab;

        /**
         * Countdown timer to auto-reject offers from bots. Uses {@link #rejCountdownLab}.
         * Created when countdown needed in {@link #update(SOCTradeOffer)}.
         * See {@link AutoRejectTask} javadoc for details.
         * @since 1.2.00
         */
        private AutoRejectTask rejTimerTask;

        SOCResourceSet give;
        SOCResourceSet get;
        int[] giveInt = new int[5];
        int[] getInt = new int[5];

        /**
         * Is the counter-offer showing? use {@link #setCounterOfferVisible(boolean)} to change.
         * @see TradeOfferPanel#counterCompactMode
         */
        boolean counterOfferMode = false;

        /**
         * Creates a new OfferPanel. Shows an opponent's offer (not the client player's)
         * and any counter-offer. The counter-offer is initially hidden.
         */
        public OfferPanel()
        {
            super(null);   // custom doLayout

            final Color hpanColor = pi.getPlayerColor(from);
            final Font offerFont = new Font("SansSerif", Font.PLAIN, 10 * displayScale);
            setFont(offerFont);
            final Color[] colors = SwingMainDisplay.getForegroundBackgroundColors(true, false);
            if (colors != null)
            {
                setForeground(colors[0]);  // Color.BLACK
                setBackground(hpanColor);
                setOpaque(true);
            }

            // All components are within either balloon or offerBox.

            /** balloon: The offer received */

            balloon = new SpeechBalloon(hpanColor, displayScale, null);
            balloon.setFont(offerFont);

            toWhom1 = new JLabel();
            balloon.add(toWhom1);

            toWhom2 = new JLabel();
            balloon.add(toWhom2);

            /** Offer's resources */
            squares = new SquaresPanel(false, displayScale);
            balloon.add(squares);

            givesYouLab = new JLabel(strings.get("trade.gives.you"));  // "Gives You:"
            givesYouLab.setToolTipText(strings.get("trade.opponent.gives"));  // "Opponent gives to you"
            balloon.add(givesYouLab);

            theyGetLab = new JLabel(strings.get("trade.they.get"));  // "They Get:"
            theyGetLab.setToolTipText(strings.get("trade.you.give"));  // "You give to opponent"
            balloon.add(theyGetLab);

            giveInt = new int[5];
            getInt = new int[5];

            final int pix2 = 2 * displayScale;
            final Insets minButtonMargin = new Insets(pix2, pix2, pix2, pix2);  // avoid text cutoff on win32 JButtons

            acceptBut = new JButton(strings.get("trade.accept"));  // "Accept"
            acceptBut.setActionCommand(ACCEPT);
            acceptBut.addActionListener(this);
            acceptBut.setFont(offerFont);
            acceptBut.setMargin(minButtonMargin);
            balloon.add(acceptBut);

            rejectBut = new JButton(strings.get("trade.reject"));  // "Reject"
            rejectBut.setActionCommand(REJECT);
            rejectBut.addActionListener(this);
            rejectBut.setFont(offerFont);
            rejectBut.setMargin(minButtonMargin);
            balloon.add(rejectBut);

            offerBut = new JButton(strings.get("trade.counter"));  // "Counter"
            offerBut.setActionCommand(OFFER);
            offerBut.addActionListener(this);
            offerBut.setFont(offerFont);
            offerBut.setMargin(minButtonMargin);
            balloon.add(offerBut);

            // Skip rejCountdownLab setup for now, because isFromRobot is false when constructed.
            // TradeOfferPanel constructor will soon call addPlayer() to set it up if needed.

            add(balloon);

            /** offerBox: The counter-offer to send */

            counterOfferBox = new ShadowedBox
                (hpanColor, colors != null ? colors[2] : null /* SwingMainDisplay.DIALOG_BG_GOLDENROD */,
                 displayScale, null);
            counterOfferBox.setVisible(false);
            counterOfferBox.setFont(offerFont);

            counterOfferToWhom = new JLabel();
            counterOfferBox.add(counterOfferToWhom);

            sendBut = new JButton(strings.get("base.send"));  // "Send"
            sendBut.setActionCommand(SEND);
            sendBut.addActionListener(this);
            sendBut.setFont(offerFont);
            sendBut.setMargin(minButtonMargin);
            counterOfferBox.add(sendBut);

            clearBut = new JButton(strings.get("base.clear"));  // "Clear"
            clearBut.setActionCommand(CLEAR);
            clearBut.addActionListener(this);
            clearBut.setFont(offerFont);
            clearBut.setMargin(minButtonMargin);
            counterOfferBox.add(clearBut);

            cancelBut = new JButton(strings.get("base.cancel"));  // "Cancel"
            cancelBut.setActionCommand(CANCEL);
            cancelBut.addActionListener(this);
            cancelBut.setFont(offerFont);
            cancelBut.setMargin(minButtonMargin);
            counterOfferBox.add(cancelBut);

            counterOfferSquares = new SquaresPanel(true, displayScale);
            counterOfferBox.add(counterOfferSquares);

            theyGetLab2 = new JLabel(strings.get("trade.they.get"));  // "They Get:"
            theyGetLab2.setToolTipText(strings.get("trade.give.to.opponent"));  // "Give to opponent"
            counterOfferBox.add(theyGetLab2);

            givesYouLab2 = new JLabel(strings.get("trade.gives.you"));  // "Gives You:"
            givesYouLab2.setToolTipText(strings.get("trade.opponent.gives"));  // "Opponent gives to you"
            counterOfferBox.add(givesYouLab2);

            add(counterOfferBox);

            /** done with counter-offer */

            // set JLabels' font/style to match their panels
            SOCDialog.styleButtonsAndLabels(balloon);
            SOCDialog.styleButtonsAndLabels(counterOfferBox);

            setSize(INITIAL_SIZE);
            setMinimumSize(INITIAL_SIZE);
            setPreferredSize(INITIAL_SIZE);
        }

        /**
         * Update the displayed offer.
         * Should be called when already showing an offer or about to switch to doing so.
         *
         * @param  offer  the trade offer, with set of resources being given and asked for
         */
        public void update(SOCTradeOffer offer)
        {
            this.give = offer.getGiveSet();
            this.get = offer.getGetSet();
            boolean[] offerList = offer.getTo();

            SOCPlayer player = hp.getGame().getPlayer(hp.getClient().getNickname());

            if (player != null)
            {
                if (! counterOffer_playerInit)
                {
                    // do we have to fill in opponent's name for 1st time?
                    counterOfferToWhom.setText
                        (strings.get("trade.counter.to.x", hp.getPlayer().getName()));  // "Counter to {0}:"

                    counterOffer_playerInit = true;
                }
                offered = offerList[player.getPlayerNumber()];
            }
            else
            {
                offered = false;
            }

            SOCGame ga = hp.getGame();

            /**
             * Build the list of player names, retrieve i18n-localized, then wrap at maxChars.
             */
            StringBuilder names = new StringBuilder();

            int cnt = 0;
            for (; cnt < ga.maxPlayers; cnt++)
            {
                if (offerList[cnt] && ! ga.isSeatVacant(cnt))
                {
                    names.append(ga.getPlayer(cnt).getName());
                    break;
                }
            }

            cnt++;

            for (; cnt < ga.maxPlayers; cnt++)
            {
                if (offerList[cnt] && ! ga.isSeatVacant(cnt))
                {
                    names.append(", ");
                    names.append(ga.getPlayer(cnt).getName());
                }
            }

            final int maxChars = ((ga.maxPlayers > 4) || ga.hasSeaBoard) ? 30 : 25;
            String names1 = strings.get("trade.offered.to", names);  // "Offered to: p1, p2, p3"
            String names2 = null;
            if (names1.length() > maxChars)
            {
                // wrap into names2
                int i = names1.lastIndexOf(", ", maxChars);
                if (i != -1)
                {
                    ++i;  // +1 to keep ','
                    names2 = names1.substring(i).trim();
                    names1 = names1.substring(0, i).trim();
                }
            }

            toWhom1.setText(names1);
            toWhom2.setText(names2 != null ? names2 : "");

            /**
             * Note: this only works if SOCResourceConstants.CLAY == 1
             */
            for (int i = 0; i < 5; i++)
            {
                giveInt[i] = give.getAmount(i + 1);
                getInt[i] = get.getAmount(i + 1);
            }
            squares.setValues(giveInt, getInt);

            if (rejCountdownLab != null)
            {
                if (rejTimerTask != null)
                    rejTimerTask.cancel();  // cancel any previous

                final int sec = pi.getBotTradeRejectSec();
                if ((sec > 0) && offered && isFromRobot && ! counterOfferMode)
                {
                    rejCountdownLab.setText(" ");  // clear any previous; not entirely blank, for other status checks
                    rejCountdownLab.setVisible(true);
                    rejTimerTask = new AutoRejectTask(sec);
                    pi.getEventTimer().scheduleAtFixedRate(rejTimerTask, 300 /* ms */, 1000 /* ms */ );
                        // initial 300ms delay, so OfferPanel should be visible at first AutoRejectTask.run()
                } else {
                    rejCountdownLab.setVisible(false);
                    rejCountdownLab.setText("");
                }
            }

            // enables accept,reject,offer Buttons if 'offered' is true
            setCounterOfferVisible(counterOfferMode);

            validate();
        }

        /**
         * Update fields when a human or robot player sits down in our {@link SOCHandPanel}'s position.
         * Must update {@link TradeOfferPanel#isFromRobot} before calling this method.
         * @since 1.2.00
         */
        void addPlayer()
        {
            if (isFromRobot)
            {
                if (rejCountdownLab == null)
                {
                    rejCountdownLab = new JLabel("");  // rejTimerTask.run() will set countdown text
                    rejCountdownLab.setForeground(null);  // inherit from panel
                    rejCountdownLab.setFont(getFont());
                    balloon.add(rejCountdownLab);
                }
            }

            if (rejCountdownLab != null)
                rejCountdownLab.setVisible(false);
        }

        /**
         * If not yet done, try to calculate the width in pixels of the text in the "Gives You:"/"They Get:" labels,
         * whichever is wider. Calculated once from FontMetrics, then cached.
         * If not available, falls back to {@link #GIVES_MIN_WIDTH} * {@code displayScale}.
         *<P>
         * {@link #givesYouLab} and {@link #theyGetLab} font must be set before calling.
         * Used by {@link #doLayout()} and {@link TradeOfferPanel#setAvailableSpace(int, int)}.
         *
         * @return  Calculated label width if FontMetrics available, otherwise {@link #GIVES_MIN_WIDTH} * {@code displayScale}
         * @see MessagePanel#calcLabelMinHeight(boolean)
         * @since 2.0.00
         */
        int calcLabelWidth()
        {
            if (givesYouLabWidth == 0)
            {
                final FontMetrics fm = getFontMetrics(givesYouLab.getFont());
                if (fm == null)
                    return GIVES_MIN_WIDTH * displayScale;

                givesYouLabWidth = Math.max
                    (fm.stringWidth(theyGetLab.getText()), fm.stringWidth(givesYouLab.getText()));
            }

            return givesYouLabWidth;
        }

        /**
         * Custom layout for this OfferPanel, including the components within
         * its offer {@link #balloon} and counter-offer {@link #counterOfferBox}.
         */
        public void doLayout()
        {
            final Dimension dim = getSize();
            int inset = 8 * displayScale;
            final boolean isUsingRejCountdownLab =
                offered && (! counterOfferMode) && (rejCountdownLab != null)
                && (rejCountdownLab.getText().length() != 0);
            final int countdownLabHeight =
                (isUsingRejCountdownLab) ? LABEL_LINE_HEIGHT * displayScale : 0;
                // If shown, use same height as toWhom1, toWhom2;
                // layout already gives extra padding above/below, so no more is needed in this calc.

            // Label text's width may increase panel width
            int w = OFFER_MIN_WIDTH * displayScale;
            final int giveW = calcLabelWidth() + (6 * displayScale);
                // from theyGetLab, givesYouLab FontMetrics; +6 is for padding before ColorSquares
            {
                int d = giveW - ((GIVES_MIN_WIDTH + 6) * displayScale);
                if (d > 0)
                    w = Math.max
                        (OFFER_MIN_WIDTH_FROM_BUTTONS * displayScale,
                         OFFER_MIN_WIDTH_FROM_LABELS * displayScale + d);
            }

            // At initial call to doLayout: dim.width, .height == 0.
            w = Math.min(w, dim.width);
            int offerH = Math.min(OFFER_HEIGHT * displayScale + countdownLabHeight, dim.height);
            // top of toWhom1 label:
            int top = (SpeechBalloon.BALLOON_POINT_SIZE + 3) * displayScale;

            if (counterOfferMode)
            {
                // also show the counter-offer controls

                final int lineH = ColorSquareLarger.HEIGHT_L * displayScale;
                offerH = Math.min((OFFER_HEIGHT - OFFER_BUTTONS_ADDED_HEIGHT) * displayScale, offerH);

                if (counterCompactMode)
                {
                    inset = 2 * displayScale;
                    balloon.setBalloonPoint(false);
                    // Will shift balloon up by BALLOON_POINT_SIZE, since we don't need to leave room for the point.
                } else {
                    balloon.setBalloonPoint(! counterHidesBalloonPoint);
                }

                // position Offer controls relative to their SpeechBalloon

                final int labelLineH = LABEL_LINE_HEIGHT * displayScale;
                toWhom1.setBounds(inset, top, w - (20 * displayScale), labelLineH);
                toWhom2.setBounds(inset, top + labelLineH, w - (20 * displayScale), labelLineH);

                int y = top + (2 * labelLineH) + (2 * displayScale);  // 2px higher than when counter-offer not showing
                givesYouLab.setBounds(inset, y, giveW, lineH);
                theyGetLab.setBounds(inset, y + lineH, giveW, lineH);
                squares.setLocation(inset + giveW, y);

                // position Counter-offer controls relative to their ShadowedBox

                final int pix4 = 4 * displayScale;
                counterOfferToWhom.setBounds(inset, pix4, w - (33 * displayScale), 12 * displayScale);
                theyGetLab2.setBounds(inset, pix4 + lineH, giveW, lineH);
                givesYouLab2.setBounds(inset, pix4 + 2*lineH, giveW, lineH);
                counterOfferSquares.setLocation(inset + giveW, pix4 + lineH);
                counterOfferSquares.doLayout();

                if (counterCompactMode)
                {
                    // Buttons to right of counterOfferToWhom, y-centered vs. height of panel
                    int buttonY =
                        ((OFFER_COUNTER_HEIGHT - BUTTON_HEIGHT - SpeechBalloon.SHADOW_SIZE - 2) - (3 * BUTTON_HEIGHT + 4))
                        * displayScale / 2;
                    final int buttonX = inset + giveW + ((SquaresPanel.WIDTH + 2) * displayScale),
                              buttonW = BUTTON_WIDTH * displayScale,
                              buttonH = BUTTON_HEIGHT * displayScale,
                              pix2 = 2 * displayScale;

                    sendBut.setBounds(buttonX, buttonY, buttonW, buttonH);
                    buttonY += buttonH + pix2;
                    clearBut.setBounds(buttonX, buttonY, buttonW, buttonH);
                    buttonY += buttonH + pix2;
                    cancelBut.setBounds(buttonX, buttonY, buttonW, buttonH);

                    int w2 = buttonX + buttonW + ((ShadowedBox.SHADOW_SIZE + 2) * displayScale);
                    if (w < w2)
                        w = w2;
                } else {
                    // Buttons below givesYouLab2, counterOfferSquares, centered across width
                    int buttonX =
                        (w - (SpeechBalloon.SHADOW_SIZE * displayScale) - ((3 * BUTTON_WIDTH + 10) * displayScale)) / 2;
                    final int buttonY = lineH + ((4 + 6 + SquaresPanel.HEIGHT) * displayScale),
                              buttonW = BUTTON_WIDTH * displayScale,
                              buttonH = BUTTON_HEIGHT * displayScale,
                              pix5 = 5 * displayScale;

                    sendBut.setBounds(buttonX, buttonY, buttonW, buttonH);
                    buttonX += pix5 + buttonW;
                    clearBut.setBounds(buttonX, buttonY, buttonW, buttonH);
                    buttonX += pix5 + buttonW;
                    cancelBut.setBounds(buttonX, buttonY, buttonW, buttonH);
                }

                if (counterCompactMode)
                {
                    // No balloon point, so top few pixels of its bounding box is empty: move it up
                    balloon.setBounds
                        (0, (-SpeechBalloon.BALLOON_POINT_SIZE) * displayScale, w, offerH);
                    counterOfferBox.setBounds(0, offerH - (SpeechBalloon.BALLOON_POINT_SIZE * displayScale),
                        w, (OFFER_COUNTER_HEIGHT - BUTTON_HEIGHT - 2) * displayScale);
                } else {
                    balloon.setBounds(0, 0, w, offerH);
                    counterOfferBox.setBounds(0, offerH, w, OFFER_COUNTER_HEIGHT * displayScale);
                }

                // If offerBox height calculation changes, please update OFFER_COUNTER_HEIGHT.

                if (rejCountdownLab != null)
                    rejCountdownLab.setVisible(false);
            }
            else
            {
                // show the offer controls, not also the counter-offer

                int balloonTop = 0;
                int buttonY = (offered)
                    ? top + (((2 * LABEL_LINE_HEIGHT) + 4 + SquaresPanel.HEIGHT + 5) * displayScale)
                    : 0;

                // if need auto-reject countdown label but balloon is not tall enough,
                // don't waste space showing its point (happens in 6-player mode
                // on same side of window as client player)
                if (isUsingRejCountdownLab)
                {
                    int htWithLab = (OFFER_HEIGHT + LABEL_LINE_HEIGHT - 2) * displayScale;
                        // if close, lose some shadow instead of point
                    boolean tooTall = (offerH < htWithLab);
                    if (tooTall)
                    {
                        final int dh = SpeechBalloon.BALLOON_POINT_SIZE * displayScale;
                        balloonTop -= dh;
                        offerH = htWithLab;
                    }
                    balloon.setBalloonPoint(! tooTall);
                } else {
                    balloon.setBalloonPoint(true);
                    if (rejCountdownLab != null)
                        rejCountdownLab.setVisible(false);  // needed after a counter-offer canceled
                }

                final int lineH = ColorSquareLarger.HEIGHT_L * displayScale,
                          labelLineH = LABEL_LINE_HEIGHT * displayScale,
                          labW = w - (20 * displayScale);

                toWhom1.setBounds(inset, top, labW, labelLineH);
                toWhom2.setBounds(inset, top + labelLineH, labW, labelLineH);
                int y = top + (2 * labelLineH) + (4 * displayScale);  // 2px lower than when counter-offer showing
                givesYouLab.setBounds(inset, y, giveW, lineH);
                theyGetLab.setBounds(inset, y + lineH, giveW, lineH);
                squares.setLocation(inset + giveW, y);
                squares.doLayout();

                if (offered)
                {
                    // center across width; if acceptBut hidden, still center as if 3 visible buttons
                    int buttonX =
                        (w - (SpeechBalloon.SHADOW_SIZE * displayScale) - ((3 * BUTTON_WIDTH + 10) * displayScale)) / 2;
                    final int buttonW = BUTTON_WIDTH * displayScale,
                              buttonH = BUTTON_HEIGHT * displayScale,
                              pix5 = 5 * displayScale;

                    acceptBut.setBounds(buttonX, buttonY, buttonW, buttonH);
                    buttonX += pix5 + buttonW;
                    rejectBut.setBounds(buttonX, buttonY, buttonW, buttonH);
                    buttonX += pix5 + buttonW;
                    offerBut.setBounds(buttonX, buttonY, buttonW, buttonH);

                    if (isUsingRejCountdownLab)
                        rejCountdownLab.setBounds
                            (pix5, buttonY + buttonH + (2 * displayScale),
                             w - inset - (SpeechBalloon.SHADOW_SIZE * displayScale), labelLineH);
                }

                balloon.setBounds(0, balloonTop, w, offerH);

                // If rejectBut height calculation changes, please update OFFER_HEIGHT.
                // If change in the height difference of "offered" buttons showing/not showing,
                // please update OFFER_BUTTONS_ADDED_HEIGHT.
            }
        }

        /**
         * Respond to button-related user input
         *
         * @param e Input event and source
         */
        public void actionPerformed(ActionEvent e)
        {
            try {
            String target = e.getActionCommand();

            if (target == OFFER)
            {
                cancelRejectCountdown();
                setCounterOfferVisible(true);
            }
            else if (target == CLEAR)
            {
                counterOfferSquares.setValues(zero, zero);
            }
            else if (target == SEND)
            {
                cancelRejectCountdown();

                SOCGame game = hp.getGame();
                SOCPlayer player = game.getPlayer(pi.getClient().getNickname());

                if (game.getGameState() == SOCGame.PLAY1)
                {
                    // slot for each resource, plus one for 'unknown' (remains 0)
                    int[] give = new int[5];
                    int[] get = new int[5];
                    int giveSum = 0;
                    int getSum = 0;
                    counterOfferSquares.getValues(give, get);

                    for (int i = 0; i < 5; i++)
                    {
                        giveSum += give[i];
                        getSum += get[i];
                    }

                    SOCResourceSet giveSet = new SOCResourceSet(give);
                    SOCResourceSet getSet = new SOCResourceSet(get);

                    if (! player.getResources().contains(giveSet))
                    {
                        pi.print("*** " + strings.get("trade.msg.cant.offer"));  // "You can't offer what you don't have."
                    }
                    else if ((giveSum == 0) || (getSum == 0))
                    {
                        pi.print("*** " + strings.get("trade.msg.must.contain"));
                            // "A trade must contain at least one resource from each player." (v1.x.xx: ... resource card ...)
                    }
                    else
                    {
                        // arrays of bools are initially false
                        boolean[] to = new boolean[game.maxPlayers];
                        // offer to the player that made the original offer
                        to[from] = true;

                        SOCTradeOffer tradeOffer =
                            new SOCTradeOffer (game.getName(),
                                               player.getPlayerNumber(),
                                               to, giveSet, getSet);
                        hp.getClient().getGameMessageSender().offerTrade(game, tradeOffer);

                        setCounterOfferVisible(true);
                    }
                }
            }

            if (target == CANCEL)
            {
                setCounterOfferVisible(false);
            }

            if (target == REJECT)
            {
                clickRejectButton();
            }

            if (target == ACCEPT)
            {
                //int[] tempGive = new int[5];
                //int[] tempGet = new int[5];
                //squares.getValues(tempGive, tempGet);

                cancelRejectCountdown();
                hp.getClient().getGameMessageSender().acceptOffer(hp.getGame(), from);
                hp.disableBankUndoButton();
            }
            } catch (Throwable th) {
                pi.chatPrintStackTrace(th);
            }
        }

        /**
         * Handle a click of the Reject button ({@link #rejectBut}):
         * Hide this panel, call {@link SOCHandPanel#rejectOfferAtClient()}.
         * @since 1.2.00
         */
        private void clickRejectButton()
        {
            setVisible(false);
            cancelRejectCountdown();
            hp.rejectOfferAtClient();
        }

        /**
         * Show or hide the Accept button, based on client player resources
         * and whether this offer is offered to client player.
         *
         * @since 1.1.20
         */
        public void updateOfferButtons()
        {
            final boolean haveResources;
            if (! offered)
            {
                haveResources = false;
            } else {
                final int cpn = hp.getPlayerInterface().getClientPlayerNumber();
                if (cpn == -1)
                    return;
                SOCPlayer player = hp.getGame().getPlayer(cpn);
                haveResources = player.getResources().contains(get);
            }

            acceptBut.setVisible(haveResources);
        }

        /**
         * show or hide our counter-offer panel, below the trade-offer panel.
         * Also shows or hides {@link #acceptBut} based on client player resources,
         * {@link #offered}, and ! {@code visible}; see also {@link #updateOfferButtons()}.
         */
        private void setCounterOfferVisible(boolean visible)
        {
            boolean haveResources = true;
            if (offered)
            {
                SOCPlayer player = hp.getGame().getPlayer(hp.getClient().getNickname());
                haveResources = player.getResources().contains(get);
            }

            counterOfferBox.setVisible(visible);

            if (! visible)
            {
                // Clear counteroffer for next use
                counterOfferSquares.setValues(zero, zero);
            }

            acceptBut.setVisible(haveResources && offered && ! visible);
            rejectBut.setVisible(offered && ! visible);
            offerBut.setVisible(offered && ! visible);
            if (rejCountdownLab != null)
            {
                if (offered && isFromRobot && (! visible) && (pi.getBotTradeRejectSec() > 0))
                    rejCountdownLab.setVisible(true);
                else
                    cancelRejectCountdown();
            }

            counterOfferMode = visible;
            recalcPreferredSize();
            hp.offerCounterOfferVisibleChanged(visible);  // calls hp.validate(), repaint()
        }

        /**
         * Will the Auto-Reject Countdown timer text be shown for this bot's offer?
         * Checks preference from {@link SOCPlayerInterface#getBotTradeRejectSec()},
         * whether {@link #isCounterOfferMode()}, and whether the reject-countdown
         * label is visible and not blank.
         *<P>
         * If visible, this countdown's height is {@link #LABEL_LINE_HEIGHT} * {@code displayScale}.
         * Even when returns true, the label may not yet be visible but space should be reserved
         * for it in {@link #doLayout()}.
         *
         * @return True if the current offer is from a bot, is offered to client player,
         *     is not counter-offer mode, and the Auto-Reject Countdown Timer label contains text.
         * @since 1.2.00
         */
        public boolean wantsRejectCountdown()
        {
            if (! (isFromRobot && (pi.getBotTradeRejectSec() > 0)))
                return false;

            // check current status
            return (! isCounterOfferMode()) && (rejCountdownLab != null)
                && (rejCountdownLab.getText().length() != 0);
        }

        /**
         * If running, cancel {@link #rejTimerTask}.
         * If showing, hide {@link #rejCountdownLab}.
         * @since 1.2.00
         */
        private void cancelRejectCountdown()
        {
            if (rejTimerTask != null)
                rejTimerTask.cancel();

            if (rejCountdownLab != null)
            {
                rejCountdownLab.setVisible(false);
                rejCountdownLab.setText("");
            }
        }

        /**
         * Event timer task to display the countdown and then reject bot's offer.
         * Started from {@link TradeOfferPanel#setOffer(SOCTradeOffer)}
         * if {@link SOCPlayerInterface#getBotTradeRejectSec()} &gt; 0.
         * Event timer calls {@link #run()} once per second.
         * Cancels itself after reaching 0, or if OfferPanel or
         * {@link TradeOfferPanel.OfferPanel#rejCountdownLab rejCountdownLab} is hidden.
         *<P>
         * Instead of calling {@link TimerTask#cancel()}, most places
         * should call {@link TradeOfferPanel.OfferPanel#cancelRejectCountdown()}.
         * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
         * @since 1.2.00
         */
        private class AutoRejectTask extends TimerTask
        {
            public int secRemain;

            /**
             * @param sec  Initial value (seconds); should be &gt; 0
             */
            public AutoRejectTask(final int sec)
            {
                secRemain = sec;
            }

            public void run()
            {
                if (! (rejCountdownLab.isVisible() && TradeOfferPanel.OfferPanel.this.isVisible()))
                {
                    rejCountdownLab.setText("");
                    cancel();
                    return;
                }

                if (secRemain > 0)
                {
                    rejCountdownLab.setText
                        (strings.get("hpan.trade.auto_reject_countdown", Integer.valueOf(secRemain)));
                        // "Auto-Reject in: 5"
                    --secRemain;
                } else {
                    clickRejectButton();
                    cancel();  // End of countdown for this timer
                }
            }
        }
    }

    /**
     * Update offer panel fields when a new player (human or robot) sits down in our {@link SOCHandPanel}'s position.
     * @since 1.2.00
     */
    public void addPlayer()
    {
        isFromRobot = pi.getGame().getPlayer(from).isRobot();
        offerPanel.addPlayer();
    }

    /**
     * Update to view the of an offer from another player.
     * If counter-offer was previously shown, show it again.
     * This lets us restore the offer view after message mode.
     *<P>
     * To update buttons after {@code setOffer} if the client player's
     * resources change, call {@link #updateOfferButtons()}.
     *<P>
     * To clear values to zero, and hide the counter-offer box,
     * call {@link #clearOffer()}.
     *
     * @param  currentOffer the trade being proposed
     * @see #isOfferToClientPlayer()
     */
    public void setOffer(SOCTradeOffer currentOffer)
    {
        offerPanel.update(currentOffer);
        recalcPreferredSize();
        invalidate();
        validate();
        repaint();
    }

    /**
     * If an offer is currently showing, show or hide Accept button based on the
     * client player's current resources.  Call this after client player receives,
     * loses, or trades resources.
     *
     * @since 1.1.20
     */
    public void updateOfferButtons()
    {
        if (! isVisible())
            return;

        offerPanel.updateOfferButtons();
    }

    /**
     * Set the offer and counter-offer contents to zero.
     * Clear counteroffer mode.
     */
    public void clearOffer()
    {
        offerPanel.squares.setValues(zero, zero);
        offerPanel.counterOfferSquares.setValues(zero, zero);
        if (offerPanel.counterOfferMode)
        {
            offerPanel.counterOfferMode = false;
            recalcPreferredSize();
            validate();
        }
        repaint();
    }

    /**
     * Is this offerpanel in counteroffer mode, with a trade offer
     * and counter-offer showing?
     * @return  true if in counter-offer mode
     * @since 1.1.08
     */
    public boolean isCounterOfferMode()
    {
        return offerPanel.counterOfferMode;
    }

    /**
     * Is panel in offer mode and is its current offer made to the client player?
     * @return  True only if {@link #isVisible()} and current offer's "made to" players list
     *     includes the client player, if any.
     * @since 1.2.01
     */
    public boolean isOfferToClientPlayer()
    {
        return isVisible() && offerPanel.offered;
    }

    /**
     * If true, hide the original offer's balloon point
     * (see {@link SpeechBalloon#setBalloonPoint(boolean)})
     * when the counter-offer is visible.
     * @since 1.1.08
     */
    public boolean doesCounterHideBalloonPoint()
    {
        return counterHidesBalloonPoint;
    }

    /**
     * If true, hide the original offer's balloon point
     * (see {@link SpeechBalloon#setBalloonPoint(boolean)})
     * when the counter-offer is visible.
     * @param hide  Hide it during counter-offer?
     * @since 1.1.08
     */
    public void setCounterHidesBalloonPoint(final boolean hide)
    {
        if (counterHidesBalloonPoint == hide)
            return;
        counterHidesBalloonPoint = hide;
        offerPanel.balloon.setBalloonPoint(! hide);
    }

    /**
     * Set or clear panel's visibility, including its OfferPanel.
     * @since 2.0.00
     */
    @Override
    public void setVisible(final boolean vis)
    {
        super.setVisible(vis);
        offerPanel.setVisible(vis);
    }

    /**
     * Move and/or resize this panel.
     * Overriden to also update "compact mode" flag for counter-offer.
     * @since 1.1.08
     */
    @Override
    public void setBounds(final int x, final int y, final int width, final int height)
    {
        super.setBounds(x, y, width, height);

        final int hpHeight = hp.getHeight();
        int counterBottomY = offerPanel.counterOfferBox.getHeight();
        if (counterBottomY > 0)
            counterBottomY += offerPanel.counterOfferBox.getY() + y + (3 * displayScale);
        counterCompactMode =
            (height < (OFFER_HEIGHT + OFFER_COUNTER_HEIGHT - OFFER_BUTTONS_ADDED_HEIGHT) * displayScale)
            || ((hpHeight > 0) &&
                (((y + height + (3 * displayScale) > hpHeight))
                 || ((counterBottomY > 0) && (counterBottomY >= hpHeight))));
    }

}  // TradeOfferPanel
