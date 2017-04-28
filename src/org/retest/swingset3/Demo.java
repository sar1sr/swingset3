/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Sun Microsystems nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.retest.swingset3;

import java.awt.Component;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.logging.Level;

import javax.swing.Icon;
import javax.swing.ImageIcon;

/**
 * Wrapper class which encapsulates a GUI component to be displayed as a SwingSet3 demo.
 *
 * @author Amy Fowler
 */
public class Demo {

	public enum State {
		UNINITIALIZED,
		INITIALIZING,
		INITIALIZED,
		RUNNING,
		STOPPED,
		FAILED
	}

	private static final String IMAGE_EXTENSIONS[] = { ".gif", ".png", ".jpg" };

	public static String deriveCategoryFromPackageName( final String className ) {
		final String parts[] = className.split( "\\." );
		// return the right-most package name
		return parts.length >= 2 ? parts[parts.length - 2] : "general";
	}

	public static String deriveCategoryFromPackageName( final Class demoClass ) {
		String packageName = demoClass.getPackage() != null ? demoClass.getPackage().getName() : null;
		if ( packageName != null ) {
			// if root package is swingset3, then remove it
			final String swingsetPackageName = Demo.class.getPackage().getName();
			if ( packageName.startsWith( swingsetPackageName + ".demos." ) ) {
				packageName = packageName.replaceFirst( swingsetPackageName + ".demos.", "" );
			}
		}
		return packageName != null ? packageName : "general";
	}

	public static String deriveNameFromClassName( final String className ) {
		final String simpleName = className.substring( className.lastIndexOf( "." ) + 1, className.length() );
		return convertToDemoName( simpleName );
	}

	public static String deriveNameFromClassName( final Class demoClass ) {
		final String className = demoClass.getSimpleName();
		return convertToDemoName( className );
	}

	private static String convertToDemoName( final String simpleClassName ) {
		final StringBuffer nameBuffer = new StringBuffer();
		if ( simpleClassName.endsWith( "Demo" ) ) {
			nameBuffer.append( simpleClassName.substring( 0, simpleClassName.indexOf( "Demo" ) ) );
			nameBuffer.append( " " );
			nameBuffer.append( "Demo" );
		}
		return nameBuffer.toString();
	}

	private final Class<?> demoClass;
	private String name;
	private String category;
	private String shortDescription; // used for tooltips
	private String iconPath;
	private Icon icon;
	private String[] sourceFilePaths;
	private URL[] sourceFiles;

	private Component component;

	private State state;

	private Exception failException;

	private PropertyChangeSupport pcs;

	public Demo( final Class<?> demoClass ) {
		this.demoClass = demoClass;

		initializeProperties();
	}

	protected void initializeProperties() {

		// First look for DemoProperties annotation if it exists
		final DemoProperties properties = demoClass.getAnnotation( DemoProperties.class );
		if ( properties != null ) {
			name = properties.value();
			category = properties.category();
			shortDescription = properties.description();
			iconPath = properties.iconFile();
			sourceFilePaths = properties.sourceFiles();
		} else {
			name = deriveNameFromClassName( demoClass );
			category = deriveCategoryFromPackageName( demoClass );
			shortDescription = "No demo description, run it to find out...";
		}
		state = State.UNINITIALIZED;
		pcs = new PropertyChangeSupport( this );
	}

	public Class getDemoClass() {
		return demoClass;
	}

	public String getName() {
		return name;
	}

	public String getCategory() {
		return category;
	}

	public Icon getIcon() {
		if ( icon == null ) {
			if ( iconPath != null && !iconPath.equals( "" ) ) {
				// icon packageName was specified in DemoProperties annotation
				icon = getIconFromPath( iconPath );
			} else {
				// Look for icon with same name as demo class
				for ( final String ext : IMAGE_EXTENSIONS ) {
					icon = getIconFromPath( getIconImagePath( ext ) );
					if ( icon != null ) {
						break;
					}
				}
			}
		}
		return icon;
	}

	protected String getIconImagePath( final String extension ) {
		// by default look for an image with the same name as the demo class
		return "resources/images/" + demoClass.getSimpleName() + extension;
	}

	private Icon getIconFromPath( final String path ) {
		Icon icon = null;
		final URL imageURL = demoClass.getResource( path );
		if ( imageURL != null ) {
			icon = new ImageIcon( imageURL );
		}
		return icon;
	}

	public String getShortDescription() {
		return shortDescription;
	}

	public URL getHTMLDescription() {
		// by default look for an html file with the same name as the demo class
		return demoClass.getResource( "resources/" + demoClass.getSimpleName() + ".html" );
	}

	public URL[] getSourceFiles() {
		// If not already cached, then look them up
		if ( sourceFiles == null ) {
			final ArrayList<URL> pathURLs = new ArrayList<>();

			// Get any names registered in DemoProperties meta-data.
			// If meta-data is not specified then sourceFilePaths contains
			// one empty string. In this case we skip it.
			if ( !(sourceFilePaths.length == 1 && sourceFilePaths[0].length() == 0) ) {
				for ( final String path : sourceFilePaths ) {
					final URL url = getClass().getClassLoader().getResource( path );
					if ( url == null ) {
						SwingSet3.logger.log( Level.WARNING, "unable to load source file '" + path + "'" );
					} else {
						pathURLs.add( url );
					}
				}
			}

			sourceFiles = pathURLs.toArray( new URL[pathURLs.size()] );
		}

		return sourceFiles;
	}

	void startInitializing() {
		setState( Demo.State.INITIALIZING );
	}

	void setDemoComponent( final Component component ) {
		if ( component != null && !demoClass.isInstance( component ) ) {
			setState( State.FAILED );
			final IllegalArgumentException e =
					new IllegalArgumentException( "component must be an instance of " + demoClass.getCanonicalName() );
			failException = e;
			throw e;
		}
		final Component old = this.component;
		this.component = component;

		if ( component != null ) {
			init();
		} else {
			setState( State.UNINITIALIZED );
		}
		pcs.firePropertyChange( "demoComponent", old, component );

	}

	public Component createDemoComponent() {
		Component component = null;
		try {
			component = (Component) demoClass.newInstance();
			setDemoComponent( component );
		} catch ( final Exception e ) {
			System.err.println( e );
			e.printStackTrace();
			failException = e;
			setState( State.FAILED );
		}
		return component;
	}

	public Component getDemoComponent() {
		return component;
	}

	public State getState() {
		return state;
	}

	protected void setState( final State state ) {
		final State oldState = this.state;
		this.state = state;
		SwingSet3.logger.log( Level.FINE, "***>" + getName() + ":setState=" + state );
		pcs.firePropertyChange( "state", oldState, state );
	}

	public void addPropertyChangeListener( final PropertyChangeListener pcl ) {
		pcs.addPropertyChangeListener( pcl );
	}

	public void removePropertyChangeListener( final PropertyChangeListener pcl ) {
		pcs.removePropertyChangeListener( pcl );
	}

	private void init() {
		setState( State.INITIALIZED );
		try {
			final Method initMethod = demoClass.getMethod( "init", (Class[]) null );
			initMethod.invoke( component, (Object[]) null );
		} catch ( final NoSuchMethodException nsme ) {
			// okay, no init method exists
		} catch ( final IllegalAccessException iae ) {
			SwingSet3.logger.log( Level.SEVERE, "unable to init demo: " + demoClass.getName(), iae );
			failException = iae;
			setState( State.FAILED );
		} catch ( final java.lang.reflect.InvocationTargetException ite ) {
			SwingSet3.logger.log( Level.SEVERE, "init method failed for demo: " + demoClass.getName(), ite );
			failException = ite;
			setState( State.FAILED );
		} catch ( final NullPointerException npe ) {
			SwingSet3.logger.log( Level.SEVERE,
					"init method called before demo was instantiated: " + demoClass.getName(), npe );
			failException = npe;
			setState( State.FAILED );
		}
	}

	public void start() {

		try {
			final Method startMethod = demoClass.getMethod( "start", (Class[]) null );
			startMethod.invoke( component, (Object[]) null );
			setState( State.RUNNING );
		} catch ( final NoSuchMethodException nsme ) {
			setState( State.RUNNING );
			// okay, no start method exists
		} catch ( final IllegalAccessException iae ) {
			SwingSet3.logger.log( Level.SEVERE, "unable to start demo: " + demoClass.getName(), iae );
			failException = iae;
			setState( State.FAILED );
		} catch ( final java.lang.reflect.InvocationTargetException ite ) {
			SwingSet3.logger.log( Level.SEVERE, "start method failed for demo: " + demoClass.getName(), ite );
			failException = ite;
			setState( State.FAILED );
		} catch ( final NullPointerException npe ) {
			SwingSet3.logger.log( Level.SEVERE,
					"start method called before demo was instantiated: " + demoClass.getName(), npe );
			failException = npe;
			setState( State.FAILED );
		}
	}

	public void stop() {
		setState( State.STOPPED );
		try {
			final Method stopMethod = demoClass.getMethod( "stop", (Class[]) null );
			stopMethod.invoke( component, (Object[]) null );

		} catch ( final NoSuchMethodException nsme ) {
			// okay, no stop method exists

		} catch ( final IllegalAccessException iae ) {
			SwingSet3.logger.log( Level.SEVERE, "unable to stop demo: " + demoClass.getName(), iae );
			failException = iae;
			setState( State.FAILED );
		} catch ( final java.lang.reflect.InvocationTargetException ite ) {
			SwingSet3.logger.log( Level.SEVERE, "stop method failed for demo: " + demoClass.getName(), ite );
			failException = ite;
			setState( State.FAILED );
		} catch ( final NullPointerException npe ) {
			SwingSet3.logger.log( Level.SEVERE,
					"stop method called before demo was instantiated: " + demoClass.getName(), npe );
		}
	}
}
