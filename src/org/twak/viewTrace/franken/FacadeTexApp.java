package org.twak.viewTrace.franken;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.vecmath.Point2d;

import org.twak.tweed.TweedFrame;
import org.twak.tweed.TweedSettings;
import org.twak.tweed.gen.SuperFace;
import org.twak.tweed.gen.skel.AppStore;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.ColourPicker;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.CGAMini;
import org.twak.viewTrace.facades.CMPLabel;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.FeatureGenerator;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.facades.PostProcessState;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

public class FacadeTexApp extends App {

	public SuperFace parent; // for non-label pipeline
	public String coarse;
	public String coarseWithWindows;
	
	public ArrayList<FRect> oldWindows; // when we create windows, we take the styles from this list
	
	MiniFacade ha;
	public String texture;
	public Color color;
	public PostProcessState postState = null;


	public TextureUVs textureUVs = TextureUVs.Square;
	public DRectangle textureRect;
	
	public FacadeTexApp( MiniFacade mf ) {
		super( );
		this.ha = mf;
		this.color = mf.wallColor;
	}

	public FacadeTexApp( FacadeTexApp fta ) {

		super( fta );
		this.parent = fta.parent;
		this.coarse = fta.coarse;
		this.coarseWithWindows = fta.coarseWithWindows;
		this.texture = fta.texture;
		this.textureUVs = fta.textureUVs;
		this.textureRect = new DRectangle(fta.textureRect);
	}

	@Override
	public App getUp(AppStore ac) {
		return ac.get( FacadeLabelApp.class, ha );
	}

	@Override
	public MultiMap<String, App> getDown(AppStore ac) {
		
		MultiMap<String, App> out = new MultiMap<>();
		
		if (postState != null)
			for (FRect r : postState.generatedWindows ) 
				out.put( "window", ac.get (PanesLabelApp.class, r ) );
		
		out.put( "super", ac.get(FacadeSuperApp.class, ha) );
		out.put( "greeble", ac.get(FacadeGreebleApp.class, ha) );
		
		return out;
	}

	@Override
	public App copy() {
		return new FacadeTexApp( this );
	}

	public final static Map<Color, Color> specLookup = new HashMap<>();
	static {
		specLookup.put( CMPLabel.Window.rgb, new Color (180, 180, 180) );
		specLookup.put( CMPLabel.Shop.rgb  , Color.darkGray );
		specLookup.put( CMPLabel.Door.rgb  , Color.gray );
	}
	
	@Override
	public void computeBatch(Runnable whenDone, List<App> batch, AppStore ass) {
		
		if ( appMode != AppMode.Net ) {
			whenDone.run();
			return;
		}
		
		NetInfo ni =NetInfo.get(this) ;
		int resolution = ni.resolution;
		
		Pix2Pix p2 = new Pix2Pix( ni );
		
		BufferedImage 
			labels = new BufferedImage( resolution, resolution, BufferedImage.TYPE_3BYTE_BGR ),
			empty  = new BufferedImage( resolution, resolution, BufferedImage.TYPE_3BYTE_BGR );
		
		Graphics2D gL = labels.createGraphics(),
				   gE = empty.createGraphics();

//		Map<MiniFacade, Meta> index = new HashMap<>();
		
//		List<MiniFacade> mfb = batch.stream().map( x -> ((FacadeTexApp)x).ha ).collect( Collectors.toList() );

		for (App a : batch) {
			
			FacadeTexApp fta = (FacadeTexApp )a;
			MiniFacade mf = fta.ha;
			
			if (!TweedSettings.settings.sitePlanInteractiveTextures && mf.featureGen instanceof CGAMini) {
				mf.featureGen = new FeatureGenerator( mf, mf.featureGen );
			}

			DRectangle mini = Pix2Pix.findBounds( mf, false, ass );

			gL.setColor( CMPLabel.Background.rgb );
			gL.fillRect( 0, 0, resolution, resolution );
			
			gE.setColor( CMPLabel.Background.rgb );
			gE.fillRect( 0, 0, resolution, resolution );

			mini = Pix2Pix.findBounds( mf, false, ass );

			if (mini == null)
				continue;
			
			DRectangle maskLabel = new DRectangle( mini );
//			mask = mask.centerSquare();

			double scale = resolution / ( Math.max( mini.height, mini.width ) + 0.4);
			
			{
				maskLabel = maskLabel.scale( scale );
				maskLabel.x = ( resolution - maskLabel.width  ) * 0.5;
				maskLabel.y = ( resolution - maskLabel.height ) * 0.5;
			}
			
//			DRectangle maskEmpty = new DRectangle(maskLabel);
//			maskEmpty.x -= resolution;

			

			if ( fta.postState == null ) {
				
				Pix2Pix.cmpRects( mf, gL, maskLabel, mini, CMPLabel.Facade.rgb, Collections.singletonList( new FRect( mini, mf ) ), ass );
				Pix2Pix.cmpRects( mf, gL, maskLabel, mini, CMPLabel.Window.rgb, mf.featureGen.getRects( Feature.WINDOW ), ass );
				
			} else {
				
				gL.setColor( CMPLabel.Facade.rgb );
				gE.setColor( CMPLabel.Facade.rgb );
				
				Stroke stroke = new BasicStroke( 0.2f * (float) scale );
				
				gL.setStroke( stroke );
				gE.setStroke( stroke );
				
				for ( Loop<? extends Point2d> l : fta.postState.wallFaces ) {
					
					Polygon p = Pix2Pix.toPoly( mf, maskLabel, mini, l ) ; 
					
					gL.fill( p );
					gL.draw( p );
					gE.fill( p );
					gE.draw( p );
				}

				stroke = new BasicStroke( 2 );
				gL.setStroke( stroke );
				gE.setStroke( stroke );
				
				for ( Loop<Point2d> l : fta.postState.occluders ) {
						Polygon poly = Pix2Pix.toPoly( mf, maskLabel, mini, l );
						gL.setColor( CMPLabel.Background.rgb );
						gE.setColor( CMPLabel.Background.rgb );
						gL.fill( poly );
						gE.fill( poly );
						gL.setColor( CMPLabel.Facade.rgb );
						gE.setColor( CMPLabel.Facade.rgb );
						gL.draw( poly );
						gE.draw( poly );
					}
				
				
				
				Pix2Pix.cmpRects( mf, gL, maskLabel, mini, CMPLabel.Window.rgb, new ArrayList<>( fta.postState.generatedWindows ), ass );// featureGen.getRects( Feature.WINDOW ) );
			}

			Meta meta = new Meta( mf, maskLabel );

			FacadeTexApp mfa = ass.get(FacadeTexApp.class, mf );
			
			p2.addInput( labels, empty, null, meta, mfa.styleZ,  FacadeLabelApp.FLOOR_HEIGHT * scale / 255. );
			
			if ( mfa.getChimneyTexture(ass) == null) {
				Meta m2 = new Meta (mf, null);

				gL.setColor( CMPLabel.Background.rgb );
				gL.fillRect( 0, 0, resolution, resolution );
				
				int inset = 20;
				gL.setColor( CMPLabel.Facade.rgb );
				gL.fillRect( inset, inset, resolution - 2*inset, resolution - 2*inset );
				
				
				p2.addInput( labels, empty, null, m2, mfa.styleZ,  0.3 );
				mfa.setChimneyTexture( ass, "in progress" );
			}
		}
		
		
		gL.dispose();
		gE.dispose();

		p2.submit( new Job( new JobResult() {

			@Override
			public void finished( Map<Object, File> results ) {

				String dest;
				try {

					for ( Map.Entry<Object, File> e : results.entrySet() ) {

						Meta meta = (Meta)e.getKey();
						
						boolean isChimney = meta.mask == null;
						
						dest = Pix2Pix.importTexture( e.getValue(), -1, specLookup, meta.mask, null, new BufferedImage[3] );

						
						FacadeTexApp mfa = ass.get(FacadeTexApp.class, meta.mf );
						
						if ( dest != null ) {
							
							if (isChimney) {
								mfa.setChimneyTexture( ass, dest );
							} else {
								mfa.coarse = mfa.texture = dest;
								mfa.coarseWithWindows = null;

								for ( FRect r : meta.mf.featureGen.getRects( Feature.WINDOW ) ) {
									
									PanesLabelApp pla = ass.get(PanesLabelApp.class, r);
									
									pla.panes = null;
									pla.texture = null;
								}
							}
							
						}
					}

				} catch ( Throwable e ) {
					e.printStackTrace();
				}
				whenDone.run();
			}
		} ) );
	}
	
	public String getChimneyTexture(AppStore ac) {
		return  ac.get (BuildingApp.class, ac.get(FacadeLabelApp.class, ha).mf.sf ).chimneyTexture ;
	}

	public void setChimneyTexture( AppStore ac, String tex ) {
		ac.get (BuildingApp.class, ac.get(FacadeLabelApp.class, ha).mf.sf ).chimneyTexture = tex;
	}

	private static class Meta {
		DRectangle mask;
		MiniFacade mf;
		
		private Meta( MiniFacade mf, DRectangle mask ) {
			this.mask = mask;
			this.mf = mf;
		}
	}
	
	public Enum[] getValidAppModes() {
		return new Enum[] {AppMode.Manual, AppMode.Bitmap, AppMode.Net};
	}
	
	@Override
	public JComponent createNetUI( Runnable globalUpdate, SelectedApps apps ) {
		
		JPanel out = new JPanel(new ListDownLayout());
		if (appMode == AppMode.Manual) {
			JButton col = new JButton("color");
			
			col.addActionListener( e -> new ColourPicker(null, color) {
				@Override
				public void picked( Color color ) {
					
					for (App a : apps)  {
						((FacadeTexApp)a).color = color;
						((FacadeTexApp)a).texture = null;
					}
					
					globalUpdate.run();
				}
			} );
			
			out.add( col );
			
		}
		
		return out;
	}

	public void resetPostProcessState() {
		postState = new PostProcessState();
	}
}
