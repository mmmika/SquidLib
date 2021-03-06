package squidpony.squidgrid.gui.gdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Align;
import squidpony.panel.IColoredString;
import squidpony.panel.IMarkup;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * An actor capable of drawing {@link IColoredString}s; it has been superseded by the newer
 * {@link TextPanel} and does not draw colors currently due to a libGDX bug. It is
 * lines-oriented: putting a line may erase a line put before. It is designed to write text
 * with a variable-width font (as opposed to {@link SquidPanel}). It performs line wrapping
 * by default. It can write from top to bottom or from bottom to top (the default).
 * 
 * <p>
 * The libGDX bug that affects IColoredString drawing here does not affect TextPanel, and
 * has been fixed in nightly builds but is not working in a stable release. It is expected
 * to be fixed in the release after 1.9.10, barring some sort of regression.
 * </p>
 * 
 * <p>
 * This class is usually used as follows:
 * 
 * <pre>
 * final int nbLines = LinesPanel.computeMaxLines(font, pixelHeight);
 * final LinesPanel<Color> lp = new LinesPanel(new GDXMarkup(), font, nbLines);
 * lp.setSize(pixelWidth, pixelHeight);
 * stage.addActor(lp);
 * </pre>
 * </p>
 * 
 * <p>
 * Contrary to {@link SquidMessageBox}, this panel doesn't support scrolling
 * (for now). So it's suited when it is fine forgetting old messages (as in
 * brogue's messages area).
 * </p>
 * 
 * @author smelC
 * @param <T>
 *
 * @see TextPanel A preferred, newer alternative that also can show variable-width fonts.
 * @see SquidMessageBox An alternative, doing similar lines-drawing business,
 *      but being backed up by {@link SquidPanel} and so limited to fixed-width fonts.
 * @deprecated You should prefer {@link TextPanel} for new code.
 */
@Deprecated
public class LinesPanel<T extends Color> extends Actor {

	/** The markup used to typeset {@link #content}. */
	protected final IMarkup<T> markup;

	/** The font used to draw {@link #content}. */
	protected final BitmapFont font;

	/** An alternative to {@link #font}, used to draw {@link #content}. */
	protected final TextCellFactory tcf;

	/** What to display. Doesn't contain {@code null} entries. */
	protected final ArrayDeque<IColoredString<T>> content;

	/** The maximal size of {@link #content} */
	protected final int maxLines;

	/**
	 * The renderer used by {@link #clearArea(Batch)}. Do not access directly:
	 * use {@link #getRenderer()} instead.
	 */
	protected /* @Nullable */ ShapeRenderer renderer;

	/**
	 * The horizontal offset to use when writing. If you aren't doing anything
	 * weird, should be left to {@code 0}.
	 */
	public float xOffset = 0;

	/**
	 * The vertical offset to use when writing. If you aren't doing anything
	 * weird, should be left to {@code 0}.
	 */
	public float yOffset = 0;

	/**
	 * If {@code true}, draws:
	 * 
	 * <pre>
	 * ...
	 * content[1]
	 * content[0]
	 * </pre>
	 * 
	 * If {@code false}, draws:
	 * 
	 * <pre>
	 * content[0]
	 * content[1]
	 * ...
	 * </pre>
	 */
	public boolean drawBottomUp = false;

	/**
	 * The color to use to clear the screen before drawing. Set it to
	 * {@code null} if you clean on your own.
	 */
	public Color clearingColor = Color.BLACK;

	/* Now comes the usual libgdx options */

	/** Whether to wrap text */
	public boolean wrap = true;

	/** The alignment used when typesetting */
	public int align = Align.left;

	/**
	 * @param markup
	 *            The markup to use, or {@code null} if none. You likely want to
	 *            give {@link GDXMarkup}. If non-{@code null}, markup will be
	 *            enabled in {@code font}.
	 * @param font
	 *            The font to use.
	 * @param maxLines
	 *            The maximum number of lines that this panel should display.
	 *            Must be {@code >= 0}.
	 * @throws IllegalStateException
	 *             If {@code maxLines < 0}
	 */
	public LinesPanel(/* @Nullable */ IMarkup<T> markup, BitmapFont font, int maxLines) {
		this.markup = markup;
		this.font = font;
		this.tcf = null;
		if (maxLines < 0)
			throw new IllegalStateException("The maximum number of lines in an instance of "
					+ getClass().getSimpleName() + " must be greater or equal than zero");
		this.maxLines = maxLines;
		this.content = new ArrayDeque<>(maxLines);
	}
	/**
	 * @param markup
	 *            The markup to use, or {@code null} if none. You likely want to
	 *            give {@link GDXMarkup}. If non-{@code null}, markup will be
	 *            enabled in {@code font}.
	 * @param font
	 *            The font to use as a TextCellFactory, typically for distance field fonts.
	 * @param maxLines
	 *            The maximum number of lines that this panel should display.
	 *            Must be {@code >= 0}.
	 * @throws IllegalStateException
	 *             If {@code maxLines < 0}
	 */

	public LinesPanel(/* @Nullable */ IMarkup<T> markup, TextCellFactory font, int maxLines) {
		this.markup = markup;
		this.tcf = font;
		this.font = font.bmpFont;
		if (maxLines < 0)
			throw new IllegalStateException("The maximum number of lines in an instance of "
					+ "LinesPanel must be greater or equal than zero");
		this.maxLines = maxLines;
		this.content = new ArrayDeque<>(maxLines);
	}

	/**
	 * Used to help find the last parameter to give the constructor of this class.
	 * @param font the font being used
	 * @param height the height of the area you want to put text into
	 * @return The last argument to give to
	 *         {@link #LinesPanel(IMarkup, BitmapFont, int)} when the
	 *         desired <b>pixel</b> height is {@code height}
	 */
	public static int computeMaxLines(BitmapFont font, float height) {
		return MathUtils.ceil(height / font.getData().lineHeight);
	}

	/**
	 * Used to help find the last parameter to give the constructor of this class.
	 * @param tcf the TextCellFactory being used
	 * @param height the height of the area you want to put text into
	 * @return The last argument to give to
	 *         {@link #LinesPanel(IMarkup, BitmapFont, int)} when the
	 *         desired <b>pixel</b> height is {@code height}
	 */
	public static int computeMaxLines(TextCellFactory tcf, float height) {
		return MathUtils.ceil(height / tcf.lineHeight);
	}

	/**
	 * Adds {@code ics} first in {@code this}, possibly removing the last entry,
	 * if {@code this}' size would grow over {@link #maxLines}.
	 *
	 * @param ics an IColoredString of the same color type as this LinesPanel; must be non-null
	 */
	public void addFirst(IColoredString<T> ics) {
		if (ics == null)
			throw new NullPointerException("Adding a null entry is forbidden");
		if (atMax())
			content.removeLast();
		content.addFirst(ics);
	}

	/**
	 * Adds {@code ics} last in {@code this}, possibly removing the first entry,
	 * if {@code this}' size would grow over {@link #maxLines}.
	 *
	 * @param ics an IColoredString of the same color type as this LinesPanel; must be non-null
	 */
	public void addLast(IColoredString<T> ics) {
		if (ics == null)
			throw new NullPointerException("Adding a null entry is forbidden");
		if (atMax())
			content.removeFirst();
		content.addLast(ics);
	}

	/**
	 * Change the first entry in {@code this} to  {@code ics}, overwriting the existing
	 * first entry if present.
	 * @param ics an IColoredString of the same color type as this LinesPanel; must be non-null
	 */
	public void setFirst(IColoredString<T> ics)
	{
		if (ics == null)
			throw new NullPointerException("Adding a null entry is forbidden");
		if(!content.isEmpty())
			content.removeFirst();
		content.addFirst(ics);
	}

	/**
	 * Change the last entry in {@code this} to  {@code ics}, overwriting the existing
	 * last entry if present.
	 * @param ics an IColoredString of the same color type as this LinesPanel; must be non-null
	 */
	public void setLast(IColoredString<T> ics)
	{
		if (ics == null)
			throw new NullPointerException("Adding a null entry is forbidden");
		if(!content.isEmpty())
			content.removeLast();
		content.addLast(ics);
	}

	/**
	 * Draws this LinesPanel using the given Batch.
	 * <br>
	 * This will set the shader of {@code batch} if using a distance field or MSDF font and the shader is currently not
	 * configured for such a font; it does not reset the shader to the default so that multiple Actors can all use the
	 * same shader and so specific extra glyphs or other items can be rendered after calling draw(). If you need to draw
	 * both a distance field font and full-color art, you should set the shader on the Batch to null when you want to
	 * draw full-color art, and end the Batch between drawing this object and the other art.
	 *
	 * @param batch a Batch such as a {@link FilterBatch} that must be between a begin() and end() call; usually done by Stage
	 * @param parentAlpha currently ignored
	 */
	@Override
	public void draw(Batch batch, float parentAlpha) {
		clearArea(batch);

		final float width = getWidth();
		if(tcf != null)
			tcf.configureShader(batch);

		final BitmapFontData data = font.getData();
		final float lineHeight = data.lineHeight;
		final float height = lineHeight * maxLines;

		final float x = getX() + xOffset;
		//TODO: check if drawBottomUp is correct or if lineHeight should be changed to 0
		float y = getY() + (drawBottomUp ? lineHeight : height - lineHeight) - data.descent + yOffset;

		final Iterator<IColoredString<T>> it = content.iterator();
//		int ydx = 0;
		float consumed = 0;
		while (it.hasNext()) {
			final IColoredString<T> ics = it.next();
			//final String str = toDraw(ics, ydx);
			/* Let's see if the drawing would go outside this Actor */
			final BitmapFontCache cache = font.getCache();
			cache.clear();
			final GlyphLayout glyph = cache.setText(ics.present(), 0, 0, width, align, wrap);
			if (height < consumed + glyph.height)
				/* We would draw outside this Actor's bounds */
				break;
			final int nbLines = MathUtils.ceil(glyph.height / lineHeight);
			/* Actually draw */
			////TODO: BitmapFontCache.setColors(float, int, int) is broken in libGDX 1.9.10; find some workaround 
//			int ci = 0;
//			ArrayList<IColoredString.Bucket<T>> frags = ics.getFragments();
//			for (int i = 0; i < frags.size(); i++) {
//				final IColoredString.Bucket<T> b = frags.get(i);
//				cache.setColors(b.getColor(), ci, ci += b.length());
//			}
			cache.setPosition(x, y);
			cache.draw(batch);
			y -= nbLines * lineHeight;
			consumed += nbLines * lineHeight;
			//ydx++;
		}
	}

	/**
	 * Paints this panel with {@link #clearingColor}
	 */
	protected void clearArea(Batch batch) {
		if (clearingColor != null) {
			batch.end();
			UIUtil.drawRectangle(getRenderer(), getX(), getY(), getWidth(), getHeight(), ShapeType.Filled,
					clearingColor);
			batch.begin();
		}
	}

	protected boolean atMax() {
		return content.size() == maxLines;

	}
	
	/**
	 * If you want to grey out "older" messages, you would do it in this method,
	 * when {@code ydx > 0} (using an {@link squidpony.IColorCenter} maybe ?).
	 * 
	 * @param ics an IColoredString with the same generic color type as this LinesPanel
	 * @param ydx
	 *            The index of {@code ics} within {@link #content}.
	 * @return A variation of {@code ics}, or {@code ics} itself.
	 */
	protected IColoredString<T> transform(IColoredString<T> ics, int ydx) {
		return ics;
	}

	protected ShapeRenderer getRenderer() {
		if (renderer == null)
			renderer = new ShapeRenderer();
		return renderer;
	}

}
