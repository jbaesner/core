package org.useware.kernel.model;

import org.junit.Before;
import org.junit.Test;
import org.useware.kernel.gui.behaviour.DialogState;
import org.useware.kernel.model.scopes.ScopeModel;
import org.useware.kernel.model.structure.Container;
import org.useware.kernel.model.structure.InteractionUnit;
import org.useware.kernel.model.structure.QName;
import org.useware.kernel.model.structure.builder.Builder;

import java.util.LinkedList;

import static org.jboss.as.console.mbui.model.StereoTypes.Form;
import static org.junit.Assert.*;
import static org.useware.kernel.model.structure.TemporalOperator.Choice;

/**
 * Verify scope assignment semantics.
 *
 * @author Heiko Braun
 * @date 4/12/13
 */
public class ScopeTest {

    private Dialog dialog;
    private static String ns = "org.jboss.transactions";
    private Container overview;
    private Container basicAttributes;
    private Container details;
    private Container processAttributes;
    private Container recoveryAttributes;

    //static final QName basicAttributes = new QName(ns, "transactionManager#basicAttributes");
    //static final QName processAttributes = new QName(ns, "transactionManager#processAttributes");
    //static final QName recoveryAttributes = new QName(ns, "transactionManager#recoveryAttributes");

    @Before
    public void setUp()
    {
        overview = new Container(ns, "transactionManager", "TransactionManager");

        basicAttributes = new Container(ns, "transactionManager#basicAttributes", "Attributes",Form);

        details = new Container(ns, "configGroups", "Details", Choice);

        processAttributes = new Container(ns, "transactionManager#processAttributes", "Process ID",Form);

        recoveryAttributes = new Container(ns, "transactionManager#recoveryAttributes", "Recovery",Form);

        // structure & mapping
        InteractionUnit root = new Builder()
                .start(overview)
                .add(basicAttributes)
                .start(details)
                .add(processAttributes)
                .add(recoveryAttributes)
                .end()
                .end()
                .build();

        this.dialog = new Dialog(QName.valueOf("org.jboss.as:transaction-subsystem"), root);
    }

    /**
     * Test assignment of scopes
     */
    @Test
    public void testScopeAssignment()
    {

        ScopeModel scopeModel = dialog.getScopeModel();
        Integer basicAttScope = scopeModel.findNode(basicAttributes.getScopeId()).getData().getId();
        Integer processAttScope = scopeModel.findNode(processAttributes.getScopeId()).getData().getId();
        Integer recoveryAttScope = scopeModel.findNode(recoveryAttributes.getScopeId()).getData().getId();

        // choice operators create separate scopes for container children
        assertNotEquals("Unit's should not share the same scope", basicAttScope, processAttScope);
        assertNotEquals("Unit's should not share the same scope", processAttScope, recoveryAttScope);
    }

    /**
     * Test resolution of statements across scope hierarchy
     */
    @Test
    public void testStatementResolution() {
        DialogState dialogState = new DialogState(dialog, new NoopContext(), new NoopStateCoordination());

        // statement resolved form parent scope
        dialogState.setStatement(basicAttributes.getId(), "foo", "bar");
        String statement = dialogState.getContext(processAttributes.getId()).resolve("foo");
        assertNotNull("Statement should be resolved from parent scope", statement);
        assertEquals("bar", statement);

        // child scope overrides statement
        dialogState.setStatement(processAttributes.getId(), "foo", "anotherBar");
        statement = dialogState.getContext(processAttributes.getId()).resolve("foo");
        assertEquals("anotherBar", statement);

        // collect statement across scopes
        LinkedList<String> statements = dialogState.getContext(processAttributes.getId()).collect("foo");
        assertTrue("Expected two statement for key 'foo'", statements.size()==2);
        assertTrue("Expected correct statement values in right order", statements.get(0).equals("anotherBar"));
        assertTrue("Expected correct statement values in right order", statements.get(1).equals("bar"));
    }

    /**
     * Deactivation of scopes.
     *
     * Rules:
     *
     * - scope siblings != unit siblings
     * - scope parents != unit parents
     * - siblings with diff. scopes deactivate each other
     * - a scope is deactivated if itself or any of it's parent scopes are deactivated
     */
    @Test
    public void testDeactivation() {
        DialogState dialogState = new DialogState(dialog, new NoopContext(), new NoopStateCoordination());

        assertTrue("Unit should be active by default", dialogState.isWithinActiveScope(processAttributes.getId()));
        assertFalse("Unit should be deactive by default", dialogState.isWithinActiveScope(recoveryAttributes.getId()));

        dialogState.activateScope(recoveryAttributes.getId());

        assertTrue("Unit should be active", dialogState.isWithinActiveScope(recoveryAttributes.getId()));
        assertFalse("Unit should be inactive", dialogState.isWithinActiveScope(processAttributes.getId()));
    }



}
