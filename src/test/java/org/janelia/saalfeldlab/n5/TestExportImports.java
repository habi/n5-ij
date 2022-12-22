package org.janelia.saalfeldlab.n5;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

import org.janelia.saalfeldlab.n5.ij.N5Exporter;
import org.janelia.saalfeldlab.n5.ij.N5Factory;
import org.janelia.saalfeldlab.n5.ij.N5Importer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import ij.ImagePlus;
import ij.gui.NewImage;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class TestExportImports
{

	private File baseDir;

	@Before
	public void before()
	{
		URL configUrl = RunImportExportTest.class.getResource( "/plugins.config" );
		baseDir = new File( configUrl.getFile()).getParentFile();
	}

	@Test
	public void testEmptyMeta()
	{
		final ImagePlus imp = NewImage.createImage("test", 8, 6, 2, 16, NewImage.FILL_NOISE);
		String metaType = N5Importer.MetadataDefaultKey;

		final String n5RootPath = baseDir + "/test_none.n5";
		final String dataset = "/test";
		final String blockSizeString = "32,32,32";
		final String compressionString = "raw";
		singleReadWriteParseTest( imp, n5RootPath, dataset, blockSizeString, metaType, compressionString, false );
	}

	@Test
	public void test4dN5v()
	{
		final int nChannels = 3;
		final int nSlices = 5;
		final ImagePlus imp = NewImage.createImage("test", 8, 6, nChannels*nSlices, 16, NewImage.FILL_NOISE);
		imp.setDimensions( nChannels, nSlices, 1 ); // 3 channels, 5 slices

		final String n5RootPath = baseDir + "/test.n5" ;
		final String dataset = "/n5v_4d";

		final N5Exporter writer = new N5Exporter();
		writer.setOptions( imp, n5RootPath, dataset, "32", N5Importer.MetadataN5ViewerKey, "gzip", N5Exporter.OVERWRITE, "");
		writer.run();

		try {
			final N5Importer reader = new N5Importer();
			reader.setShow( false );
			for( int i = 0; i < nChannels; i++)
			{
				final String n5PathAndDataset = String.format("%s/%s/c%d/s0", n5RootPath, dataset, i);
				final List< ImagePlus > impList = reader.process( n5PathAndDataset, false );
				Assert.assertEquals("n5v load channel", 1, impList.size());
				Assert.assertTrue("n5v channel equals", equalChannel(imp, i, impList.get(0)));
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			Assert.fail();
		}
	}
	
	@Test
	public void testReadWriteParse()
	{
		final HashMap<String,String> typeToExtension = new HashMap<>();
		typeToExtension.put( "FILESYSTEM", "n5" );
		typeToExtension.put( "ZARR", "zarr" );
		typeToExtension.put( "HDF5", "h5" );

		final String blockSizeString = "16,16,16";
		final String compressionString = "gzip";
		String[] containerTypes = new String[] { "FILESYSTEM", "ZARR", "HDF5" };

		final String[] metadataTypes = new String[]{
				N5Importer.MetadataImageJKey,
				N5Importer.MetadataN5CosemKey,
				N5Importer.MetadataN5ViewerKey
		};

		for( int bitDepth : new int[]{ 8, 16, 32 })
		{
			final ImagePlus imp = NewImage.createImage("test", 8, 6, 4, bitDepth, NewImage.FILL_NOISE);
			imp.setDimensions( 1, 4, 1 );
			for( final String containerType : containerTypes )
			{
				for( final String metatype : metadataTypes )
				{
					final String n5RootPath = baseDir + "/test." + typeToExtension.get( containerType );
					final String datasetBase = "/test_"+metatype+"_"+bitDepth;
					final String dataset = datasetBase;

					singleReadWriteParseTest( imp, n5RootPath, dataset, blockSizeString, metatype, compressionString, true );
				}
			}
		}
	}

	public static < T extends RealType< T > & NativeType< T > > boolean equal( final ImagePlus a, final ImagePlus b )
	{
		try {
			final Img<T> imgA = ImageJFunctions.wrapRealNative( a );
			final Img<T> imgB = ImageJFunctions.wrapRealNative( b );

			final Cursor< T > c = imgA.cursor();
			final RandomAccess< T > r = imgB.randomAccess();

			while( c.hasNext() )
			{
				c.fwd();
				r.setPosition( c );
				if( c.get().getRealDouble() != r.get().getRealDouble() )
					return false;
			}
			return true;
		}catch( final Exception e )
		{
			return false;
		}
	}

	/**
	 * Checks that image b is equal to channel c of
	 * @param <T>
	 * @param a
	 * @param b
	 * @return
	 */
	private static < T extends RealType< T > & NativeType< T > > boolean equalChannel( final ImagePlus all, final int i, final ImagePlus cimg )
	{
		try {
			final Img<T> imgAll = ImageJFunctions.wrapRealNative( all  );
			final Img<T> imgC = ImageJFunctions.wrapRealNative( cimg );

			IntervalView<T> channelGtImg = Views.hyperSlice( imgAll, 2, i);
			final Cursor< T > c = channelGtImg.cursor();
			final RandomAccess< T > r = imgC.randomAccess();
			while( c.hasNext() )
			{
				c.fwd();
				r.setPosition( c );
				if( c.get().getRealDouble() != r.get().getRealDouble() )
					return false;
			}
			return true;
		}catch( final Exception e )
		{
			return false;
		}
	}

	private static boolean equalRGB( final ImagePlus a, final ImagePlus b )
	{
		try {
			final Img<ARGBType> imgA = ImageJFunctions.wrapRGBA( a );
			final Img<ARGBType> imgB = ImageJFunctions.wrapRGBA( b );

			final Cursor< ARGBType > c = imgA.cursor();
			final RandomAccess< ARGBType > r = imgB.randomAccess();

			while( c.hasNext() )
			{
				c.fwd();
				r.setPosition( c );
				if( c.get().get() != r.get().get() )
					return false;
			}

			return true;

		}catch( final Exception e )
		{
			return false;
		}
	}

	public void singleReadWriteParseTest(
			final ImagePlus imp,
			final String outputPath,
			final String dataset,
			final String blockSizeString,
			final String metadataType,
			final String compressionType,
			boolean testMeta )
	{
		final N5Exporter writer = new N5Exporter();
		writer.setOptions( imp, outputPath, dataset, blockSizeString, metadataType, compressionType,
				N5Exporter.OVERWRITE, "");
		writer.run(); // run() closes the n5 writer

		final String readerDataset = metadataType.equals( N5Importer.MetadataN5ViewerKey ) ? dataset + "/c0/s0" : dataset;
		final String n5PathAndDataset = outputPath + readerDataset;

		final N5Importer reader = new N5Importer();
		reader.setShow( false );
		final List< ImagePlus > impList = reader.process( n5PathAndDataset, false );

		assertEquals( String.format( "%s %s one image opened ", outputPath, dataset ), 1, impList.size() );

		final ImagePlus impRead = impList.get( 0 );

		if( testMeta )
		{
			boolean resEqual = impRead.getCalibration().pixelWidth == imp.getCalibration().pixelWidth && 
					impRead.getCalibration().pixelHeight == imp.getCalibration().pixelHeight
					&& impRead.getCalibration().pixelDepth == imp.getCalibration().pixelDepth;

			assertTrue( String.format( "%s resolutions ", dataset ), resEqual );

			boolean unitsEqual = impRead.getCalibration().getUnit().equals( imp.getCalibration().getUnit() );
			assertTrue( String.format( "%s units ", dataset ), unitsEqual );
		}

		boolean imagesEqual;
		if( imp.getType() == ImagePlus.COLOR_RGB )
		{
			imagesEqual = equalRGB( imp, impRead );
			assertEquals( String.format( "%s as rgb ", dataset ), ImagePlus.COLOR_RGB, impRead.getType() );
		}
		else
			imagesEqual = equal( imp, impRead );

		assertTrue( String.format( "%s data ", dataset ), imagesEqual );

		try {
			N5Writer n5w = new N5Factory().openWriter(outputPath);
			n5w.remove();
		} catch (IOException e) {
			e.printStackTrace();
		}

		impRead.close();

	}

	@Test
	public void testRgb()
	{
		final ImagePlus imp = NewImage.createRGBImage("test", 8, 6, 4, NewImage.FILL_NOISE);
		String metaType = N5Importer.MetadataImageJKey;

		final String n5RootPath = baseDir + "/test_rgb.n5";
		final String dataset = "/ij";
		final String blockSizeString = "16,16,16";
		final String compressionString = "raw";

		singleReadWriteParseTest( imp, n5RootPath, dataset, blockSizeString, metaType, compressionString, false );
	}

	/**
	 * A test if we ever expand n5-viewer style metadata to be able 
	 * to describe arrays of more than 3 dimensions.
	 * 
	 */
//	@Test
//	public void testMultiChannelN5V()
//	{
//		final int bitDepth = 8;
//		final ImagePlus imp = NewImage.createImage("test", 8, 6, 4*3, bitDepth, NewImage.FILL_NOISE);
//		imp.setDimensions( 3, 4, 1 );
//		imp.getCalibration().pixelWidth = 0.5;
//		imp.getCalibration().pixelHeight = 0.6;
//		imp.getCalibration().pixelDepth = 0.7;
//
//		String metatype = N5Importer.MetadataN5ViewerSingleKey;
//		final String n5RootPath = baseDir + "/test_n5v_mcSingle.n5";
//		final String dataset = "/n5vs";
//		final String blockSizeString = "16,16,16,16";
//		final String compressionString = "raw";
//
//		singleReadWriteParseTest( imp, n5RootPath, dataset, blockSizeString, metatype, compressionString, true );
//	}
}
