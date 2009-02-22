/*
 * Licensed to "Neo Technology," Network Engine for Objects in Lund AB
 * (http://neotechnology.com) under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. Neo Technology licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at (http://www.apache.org/licenses/LICENSE-2.0). Unless required by
 * applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.neo4j.neoclipse.reltype;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.zest.core.viewers.GraphViewer;
import org.neo4j.api.core.Direction;
import org.neo4j.api.core.Node;
import org.neo4j.api.core.Relationship;
import org.neo4j.api.core.RelationshipType;
import org.neo4j.neoclipse.NeoIcons;
import org.neo4j.neoclipse.view.NeoGraphLabelProvider;
import org.neo4j.neoclipse.view.NeoGraphLabelProviderWrapper;
import org.neo4j.neoclipse.view.NeoGraphViewPart;

public class RelationshipTypeView extends ViewPart implements
    ISelectionListener
{
    public final static String ID = "org.neo4j.neoclipse.reltype.RelationshipTypeView";
    protected static final int OK = 0;
    private TableViewer viewer;
    private Action markIncomingAction;
    private Action markOutgoingAction;
    private Action clearMarkedAction;
    private Action doubleClickAction;
    private RelationshipTypesProvider provider;
    private NeoGraphViewPart graphView = null;
    private NeoGraphLabelProvider graphLabelProvider = NeoGraphLabelProviderWrapper
        .getInstance();
    private Action newAction;

    class NameSorter extends ViewerSorter
    {
        @Override
        public int compare( Viewer viewer, Object e1, Object e2 )
        {
            if ( e1 instanceof RelationshipType
                && e2 instanceof RelationshipType )
            {
                return ((RelationshipType) e1).name().compareTo(
                    ((RelationshipType) e2).name() );
            }
            return super.compare( viewer, e1, e2 );
        }
    }

    /**
     * The constructor.
     */
    public RelationshipTypeView()
    {
    }

    /**
     * This is a callback that will allow us to create the viewer and initialize
     * it.
     */
    public void createPartControl( Composite parent )
    {
        viewer = new TableViewer( parent, SWT.MULTI | SWT.V_SCROLL );
        provider = new RelationshipTypesProvider();
        viewer.setContentProvider( provider );
        viewer.setLabelProvider( NeoGraphLabelProviderWrapper.getInstance() );
        viewer.setSorter( new NameSorter() );
        viewer.setInput( getViewSite() );

        // Create the help context id for the viewer's control
        PlatformUI.getWorkbench().getHelpSystem().setHelp( viewer.getControl(),
            "org.neo4j.neoclipse.reltypesviewer" );
        makeActions();
        hookContextMenu();
        hookDoubleClickAction();
        contributeToActionBars();
        getSite().getPage().addSelectionListener( NeoGraphViewPart.ID, this );
        for ( IViewReference view : getSite().getPage().getViewReferences() )
        {
            if ( NeoGraphViewPart.ID.equals( view.getId() ) )
            {
                graphView = (NeoGraphViewPart) view.getView( false );
            }
        }
        getSite().setSelectionProvider( viewer );
        getSite().getPage().addSelectionListener( ID, this );
    }

    private void hookContextMenu()
    {
        MenuManager menuMgr = new MenuManager( "#PopupMenu" );
        menuMgr.setRemoveAllWhenShown( true );
        menuMgr.addMenuListener( new IMenuListener()
        {
            public void menuAboutToShow( IMenuManager manager )
            {
                RelationshipTypeView.this.fillContextMenu( manager );
            }
        } );
        Menu menu = menuMgr.createContextMenu( viewer.getControl() );
        viewer.getControl().setMenu( menu );
        getSite().registerContextMenu( menuMgr, viewer );
    }

    private void contributeToActionBars()
    {
        IActionBars bars = getViewSite().getActionBars();
        // fillLocalPullDown( bars.getMenuManager() );
        fillLocalToolBar( bars.getToolBarManager() );
    }

    // private void fillLocalPullDown( IMenuManager manager )
    // {
    // manager.add( markIncomingAction );
    // manager.add( markOutgoingAction );
    // manager.add( clearMarkedAction );
    // }

    private void fillContextMenu( IMenuManager manager )
    {
        manager.add( markIncomingAction );
        manager.add( markOutgoingAction );
        // Other plug-ins can contribute there actions here
        manager.add( new Separator( IWorkbenchActionConstants.MB_ADDITIONS ) );
    }

    private void fillLocalToolBar( IToolBarManager manager )
    {
        manager.add( markIncomingAction );
        manager.add( markOutgoingAction );
        manager.add( clearMarkedAction );
        manager.add( newAction );
    }

    private void makeActions()
    {
        markIncomingAction = new Action( "Mark incoming" )
        {
            public void run()
            {
                RelationshipType relType = getCurrentRelType();
                if ( relType == null )
                {
                    return;
                }
                highlightNodes( relType, Direction.INCOMING );
                clearMarkedAction.setEnabled( true );
            }
        };
        markIncomingAction
            .setToolTipText( "Mark nodes with incoming relationship of this type." );
        markIncomingAction.setImageDescriptor( NeoIcons.INCOMING
            .getDescriptor() );
        markIncomingAction.setEnabled( false );

        markOutgoingAction = new Action( "Mark outgoing" )
        {
            public void run()
            {
                RelationshipType relType = getCurrentRelType();
                if ( relType == null )
                {
                    return;
                }
                highlightNodes( relType, Direction.OUTGOING );
                clearMarkedAction.setEnabled( true );
            }
        };
        markOutgoingAction
            .setToolTipText( "Mark nodes with outgoing relationship of this type." );
        markOutgoingAction.setImageDescriptor( NeoIcons.OUTGOING
            .getDescriptor() );
        markOutgoingAction.setEnabled( false );

        clearMarkedAction = new Action( "Clear marked elements" )
        {
            public void run()
            {
                graphLabelProvider.clearMarkedNodes();
                graphLabelProvider.clearMarkedRels();
                graphView.getViewer().refresh( true );
                setEnabled( false );
            }
        };
        clearMarkedAction.setImageDescriptor( NeoIcons.CLEAR.getDescriptor() );
        clearMarkedAction.setEnabled( false );

        newAction = new Action( "Create new" )
        {
            public void run()
            {
                InputDialog input = new InputDialog( null,
                    "New relationship type entry",
                    "Please enter the name for the new relationships type",
                    null, null );
                if ( input.open() == OK && input.getReturnCode() == OK )
                {
                    RelationshipType relType = createRelationshipType( input
                        .getValue() );
                    provider.addFakeType( relType );
                    viewer.refresh();
                }
            }
        };
        newAction.setToolTipText( "Create new relationship type." );
        newAction.setImageDescriptor( NeoIcons.NEW.getDescriptor() );

        doubleClickAction = new Action( "Mark relationships" )
        {
            public void run()
            {
                RelationshipType relType = getCurrentRelType();
                if ( relType == null )
                {
                    return;
                }
                highlightRelationshipType( relType );
                setEnableRestrictedActions( true );
                clearMarkedAction.setEnabled( true );
            }
        };
    }

    private void setEnableRestrictedActions( boolean enabled )
    {
        markIncomingAction.setEnabled( enabled );
        markOutgoingAction.setEnabled( enabled );
    }

    private RelationshipType getCurrentRelType()
    {
        ISelection selection = viewer.getSelection();
        Object obj = ((IStructuredSelection) selection).getFirstElement();
        if ( obj instanceof RelationshipType )
        {
            return (RelationshipType) obj;
        }
        return null;
    }

    private void highlightRelationshipType( RelationshipType relType )
    {
        if ( graphView == null )
        {
            return;
        }
        List<Relationship> rels = new ArrayList<Relationship>();
        GraphViewer gViewer = graphView.getViewer();
        for ( Object o : gViewer.getConnectionElements() )
        {
            if ( o instanceof Relationship )
            {
                Relationship rel = (Relationship) o;
                if ( rel.getType().equals( relType ) )
                {
                    rels.add( rel );
                }
            }
        }
        // gViewer.setSelection( new StructuredSelection( rels ), true );
        graphLabelProvider.addMarkedRels( rels );
        gViewer.refresh( true );
    }

    private void highlightNodes( RelationshipType relType, Direction direction )
    {
        if ( graphView == null )
        {
            return;
        }
        GraphViewer gViewer = graphView.getViewer();
        Set<Node> nodes = new HashSet<Node>();
        for ( Object o : gViewer.getNodeElements() )
        {
            if ( o instanceof Node )
            {
                Node node = (Node) o;
                if ( node.hasRelationship( relType, direction ) )
                {
                    nodes.add( node );
                }
            }
        }
        // gViewer.setSelection( new StructuredSelection( nodes ), true );
        graphLabelProvider.addMarkedNodes( nodes );
        gViewer.refresh( true );
    }

    private void hookDoubleClickAction()
    {
        viewer.addDoubleClickListener( new IDoubleClickListener()
        {
            public void doubleClick( DoubleClickEvent event )
            {
                doubleClickAction.run();
            }
        } );
    }

    private RelationshipType createRelationshipType( final String name )
    {
        return new RelationshipType()
        {
            public String name()
            {
                return name;
            }
        };
    }

    /**
     * Passing the focus request to the viewer's control.
     */
    public void setFocus()
    {
        viewer.getControl().setFocus();
    }

    /**
     * Keep track of the graph view selections.
     */
    public void selectionChanged( IWorkbenchPart part, ISelection selection )
    {
        if ( !(selection instanceof IStructuredSelection) )
        {
            return;
        }
        IStructuredSelection parSs = (IStructuredSelection) selection;
        Object firstElement = parSs.getFirstElement();
        if ( part instanceof NeoGraphViewPart )
        {
            graphView = (NeoGraphViewPart) part;
            if ( firstElement instanceof Relationship )
            {
                RelationshipType currentSelection = ((Relationship) firstElement)
                    .getType();
                viewer
                    .setSelection( new StructuredSelection( currentSelection ) );
                setEnableRestrictedActions( true );
            }
        }
        else if ( this.equals( part ) )
        {
            if ( selection.isEmpty() )
            {
                setEnableRestrictedActions( false );
            }
            else
            {
                setEnableRestrictedActions( true );
            }
        }
    }
}