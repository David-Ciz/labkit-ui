package net.imglib2.algorithm.features.labeling;

import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.type.logic.BitType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Matthias Arzt
 */
public class SparseRoiTest {

	Interval interval = new FinalInterval(1000, 1000, 1000);

	long[] positionA = new long[]{42,42,42};

	long[] positionB = new long[]{10,10,10};

	@Test
	public void testRandomAccess() {
		SparseRoi roi = new SparseRoi(interval);
		RandomAccess<BitType> ra = roi.randomAccess();
		ra.setPosition(positionA);
		assertFalse(ra.get().get());
		ra.get().set(true);
		ra.setPosition(positionB);
		assertFalse(ra.get().get());
		ra.setPosition(positionA);
		assertTrue(ra.get().get());
	}

	@Test
	public void testSize() {
		SparseRoi roi = new SparseRoi(interval);
		RandomAccess<BitType> ra = roi.randomAccess();
		ra.setPosition(positionA);
		ra.get().set(true);
		assertEquals(1, roi.size());
	}

	@Test
	public void testCursor() {
		SparseRoi roi = new SparseRoi(interval);
		RandomAccess<BitType> ra = roi.randomAccess();
		ra.setPosition(positionA);
		ra.get().set(true);
		Cursor<Void> cursor = roi.cursor();
		assertTrue(cursor.hasNext());
		cursor.fwd();
		assertEquals(positionA[1], cursor.getLongPosition(1));
		assertFalse(cursor.hasNext());
	}
}
