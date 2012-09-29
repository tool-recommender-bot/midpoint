/*
 * Copyright (c) 2012 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2012 [name of copyright owner]
 */

package com.evolveum.midpoint.wf.processes.addroles;

import com.evolveum.midpoint.model.api.context.ModelContext;
import com.evolveum.midpoint.model.api.context.ModelElementContext;
import com.evolveum.midpoint.model.api.context.ModelState;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.wf.WfConstants;
import com.evolveum.midpoint.wf.WfHook;
import com.evolveum.midpoint.wf.WfTaskUtil;
import com.evolveum.midpoint.wf.activiti.ActivitiUtil;
import com.evolveum.midpoint.wf.messages.ProcessEvent;
import com.evolveum.midpoint.wf.processes.ProcessWrapper;
import com.evolveum.midpoint.wf.processes.StartProcessInstruction;
import com.evolveum.midpoint.xml.ns._public.common.common_2.*;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author mederly
 */
@Component
public class AddRolesProcessWrapper implements ProcessWrapper {

    private static final Trace LOGGER = TraceManager.getTrace(AddRolesProcessWrapper.class);

    @Autowired(required = true)
    private RepositoryService repositoryService;

    @Autowired(required = true)
    private WfHook wfHook;

    @Autowired(required = true)
    private WfTaskUtil wfTaskUtil;

    public static final String USER_NAME = "userName";
    public static final String ROLES_TO_APPROVE = "rolesToApprove";
    public static final String DECISION_LIST = "decisionList";
    private static final String ADD_ROLE_PROCESS = "AddRoles";

    @PostConstruct
    public void register() {
        wfHook.registerWfProcessWrapper(this);
    }

    @Override
    public StartProcessInstruction startProcessIfNeeded(ModelContext context, Task task, OperationResult result) {

        if (context.getState() != ModelState.PRIMARY) {
            return null;
        }

        ObjectDelta<? extends ObjectType> change = context.getFocusContext().getPrimaryDelta();

        /*
        * We either add a user; then the list of roles to be added is given by the assignment property,
        * or we modify a user; then the list of roles is given by the assignment property modification.
        */

        List<RoleType> rolesToAdd = new ArrayList<RoleType>();

        if (change.getChangeType() == ChangeType.ADD) {

            PrismObject<?> prismToAdd = change.getObjectToAdd();
            boolean isUser = prismToAdd.getCompileTimeClass().isAssignableFrom(UserType.class);

            if (!isUser) {
                return null;
            }

            UserType user = (UserType) prismToAdd.asObjectable();

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Role-related assignments in user add delta (" + user.getAssignment().size() + "): ");
            }

            for (AssignmentType a : user.getAssignment()) {
                ObjectReferenceType ort = a.getTargetRef();
                if (ort != null && RoleType.COMPLEX_TYPE.equals(ort.getType())) {
                    RoleType role = resolveRoleRef(a, result);
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace(" - role: " + role);
                    }
                    rolesToAdd.add(role);
                }
            }
        } else if (change.getChangeType() == ChangeType.MODIFY) {

            boolean isUser = change.getObjectTypeClass().isAssignableFrom(UserType.class);

            if (!isUser) {
                return null;
            }

            for (ItemDelta delta : change.getModifications()) {
                if (UserType.F_ASSIGNMENT.equals(delta.getName())) {
                    for (Object o : delta.getValuesToAdd()) {
                        //if (LOGGER.isTraceEnabled()) {
                            LOGGER.info("Assignment to add = " + ((PrismContainerValue) o).dump());
                        //}
                        LOGGER.info("isTraceEnabled: " + LOGGER.isTraceEnabled());
                        PrismContainerValue<AssignmentType> at = (PrismContainerValue<AssignmentType>) o;
                        ObjectReferenceType ort = at.getValue().getTargetRef();
                        if (ort != null && RoleType.COMPLEX_TYPE.equals(ort.getType())) {
                            RoleType role = resolveRoleRef(at.getValue(), result);
                            if (LOGGER.isTraceEnabled()) {
                                LOGGER.trace(" - role: " + role);
                            }
                            rolesToAdd.add(role);
                        }
                    }
                }
            }
        } else {
            return null;            // DELETE does not need role approval
        }

        if (!rolesToAdd.isEmpty()) {

            ModelElementContext<UserType> fc = context.getFocusContext();
            UserType oldUser = fc.getObjectOld() != null ? fc.getObjectOld().asObjectable() : null;
            UserType newUser = fc.getObjectNew() != null ? fc.getObjectNew().asObjectable() : null;

            StartProcessInstruction spi = new StartProcessInstruction();
            spi.setProcessName(ADD_ROLE_PROCESS);
            spi.setSimple(true);
            String rolesAsList = formatAsRoleList(rolesToAdd);
            spi.setTaskName("Workflow for approving adding " + rolesAsList + " to " + newUser.getName());
            spi.addProcessVariable(WfConstants.VARIABLE_PROCESS_NAME, "Adding " + rolesAsList + " to " + newUser.getName());
            spi.addProcessVariable(WfConstants.VARIABLE_START_TIME, new Date());
            spi.addProcessVariable(USER_NAME, newUser.getName());
            spi.addProcessVariable(ROLES_TO_APPROVE, rolesToAdd);
            spi.addProcessVariable(DECISION_LIST, new DecisionList());
            String objectOid = null;
            if (fc.getObjectNew() != null && fc.getObjectNew().getOid() != null) {
                objectOid = fc.getObjectNew().getOid();
            } else if (fc.getObjectOld() != null && fc.getObjectOld().getOid() != null) {
                objectOid = fc.getObjectOld().getOid();
            }
            spi.addProcessVariable(WfConstants.VARIABLE_MIDPOINT_OBJECT_OID, objectOid);
            spi.addProcessVariable(WfConstants.VARIABLE_MIDPOINT_OBJECT_OLD, fc.getObjectOld());
            spi.addProcessVariable(WfConstants.VARIABLE_MIDPOINT_OBJECT_NEW, fc.getObjectNew());
            //spi.addProcessVariable(WfConstants.VARIABLE_MIDPOINT_DELTA, change);
            spi.addProcessVariable(WfConstants.VARIABLE_MIDPOINT_REQUESTER, task.getOwner());   // todo - is this ok?
            spi.addProcessVariable(WfConstants.VARIABLE_MIDPOINT_REQUESTER_OID, task.getOwner().getOid());
            spi.addProcessVariable(WfConstants.VARIABLE_UTIL, new ActivitiUtil());
            spi.addProcessVariable(WfConstants.VARIABLE_MIDPOINT_ADDITIONAL_DATA, "@role");
            return spi;
        }
        return null;
    }

    private String formatAsRoleList(List<RoleType> rolesToAdd) {
        StringBuffer sb = new StringBuffer();
        if (rolesToAdd.size() > 1) {
            sb.append("roles ");
        } else if (rolesToAdd.size() == 1) {
            sb.append("role ");
        } else {
            throw new IllegalStateException("No roles to approve.");
        }
        boolean first = true;
        for (RoleType rt : rolesToAdd) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(rt.getName());
        }
        return sb.toString();
    }


    private RoleType resolveRoleRef(AssignmentType a, OperationResult result) {
        RoleType role = (RoleType) a.getTarget();
        if (role == null) {
            try {
                role = repositoryService.getObject(RoleType.class, a.getTargetRef().getOid(), result).asObjectable();
            } catch (ObjectNotFoundException e) {
                throw new SystemException(e);
            } catch (SchemaException e) {
                throw new SystemException(e);
            }
            a.setTarget(role);
        }
        return role;
    }

    @Override
    public boolean finishProcess(ModelContext context, ProcessEvent event, Task task, OperationResult result) {

        DecisionList decisionList = (DecisionList) event.getVariables().get(DECISION_LIST);
        if (decisionList == null) {
            throw new IllegalStateException("DecisionList is null");           // todo
        }

        if (context.getState() != ModelState.PRIMARY) {
            throw new IllegalStateException("Model state is not PRIMARY (it is " + context.getState() + "); task = " + task);
        }

        List<String> rolesApproved = new ArrayList<String>();
        List<String> rolesDisapproved = new ArrayList<String>();
        for (Decision decision : decisionList.getDecisionList()) {
            LOGGER.info("Workflow result: approved=" + decision.isApproved() + " for role=" + decision.getRole());
            if (decision.isApproved()) {
                rolesApproved.add(decision.getRole().getOid());
            } else {
                rolesDisapproved.add(decision.getRole().getOid());
            }
        }

        ObjectDelta<? extends ObjectType> change = context.getFocusContext().getPrimaryDelta();
        if (change.getChangeType() == ChangeType.ADD) {

            PrismObject<?> prismToAdd = change.getObjectToAdd();
            boolean isUser = prismToAdd.getCompileTimeClass().isAssignableFrom(UserType.class);

            if (!isUser) {
                throw new IllegalStateException("Object to be added is not User; task = " + task);
            }

            UserType user = (UserType) prismToAdd.asObjectable();
            LOGGER.info("Assignments (" + user.getAssignment().size() + "): ");
            for (AssignmentType a : new ArrayList<AssignmentType>(user.getAssignment())) {      // copy, because we want to modify the list
                ObjectReferenceType ort = a.getTargetRef();
                LOGGER.info("ort = " + ort);
                LOGGER.info("ort.getType = " + ort.getType());
                if (RoleType.COMPLEX_TYPE.equals(ort.getType())) {
                    LOGGER.info(" - role: " + ort);
                    if (rolesApproved.contains(ort.getOid())) {
                        LOGGER.info(" --- approved");
                    } else {
                        LOGGER.info(" --- rejected; will be removed from the list");
                        user.getAssignment().remove(a);
                    }
                }
            }
        } else if (change.getChangeType() == ChangeType.MODIFY) {

            boolean isUser = change.getObjectTypeClass().isAssignableFrom(UserType.class);

            if (!isUser) {
                throw new IllegalStateException("Object to be added is not User; task = " + task);
            }

            for (ItemDelta delta : change.getModifications()) {
                if (UserType.F_ASSIGNMENT.equals(delta.getName())) {
                    for (Object o : new ArrayList<Object>(delta.getValuesToAdd())) {
                        LOGGER.info("Value to add = " + o);
                        PrismContainerValue<AssignmentType> at = (PrismContainerValue<AssignmentType>) o;
                        ObjectReferenceType ort = at.getValue().getTargetRef();
                        LOGGER.info("ort = " + ort);
                        LOGGER.info("ort.getType = " + ort.getType());
                        if (RoleType.COMPLEX_TYPE.equals(ort.getType())) {
                            LOGGER.info(" - role: " + ort);
                            if (rolesApproved.contains(ort.getOid())) {
                                LOGGER.info(" --- approved");
                            } else {
                                LOGGER.info(" --- rejected; will be removed from the list");
                                delta.getValuesToAdd().remove(o);
                            }
                        }
                    }
                }
            }
        } else {
            throw new IllegalStateException("Operation that has to be continued is neither ADD nor MODIFY; task = " + task);
        }

        return true;

    }

    // todo this is brutal hack
    @Override
    public String getProcessSpecificDetails(ProcessInstance instance, Map<String, Object> vars, List<org.activiti.engine.task.Task> tasks) {
        StringBuffer sb = new StringBuffer();
        sb.append("The following roles were requested to be added:\n");
        List<RoleType> roles = (List<RoleType>) vars.get(ROLES_TO_APPROVE);
        for (RoleType role : roles) {
            sb.append(" - " + role.getName() + " [" + role.getOid() + "]\n");
        }
        sb.append("\nDecisions done up to now: ");
        DecisionList decisionList = (DecisionList) vars.get(DECISION_LIST);
        if (decisionList == null || decisionList.getDecisionList() == null) {
            sb.append("(error - DecisionList is null)\n");        // todo
        } else if (decisionList.getDecisionList().isEmpty()) {
            sb.append("none\n");
        } else {
            sb.append("\n");
            for (Decision decision : decisionList.getDecisionList()) {
                sb.append(" - role: " + decision.getRole() + ", approver: " + decision.getUser() + ", result: " + decision.isApproved() + ", comment: " + decision.getComment() + "\n");
            }
        }

        return sb.toString();
    }

    // todo this is brutal hack
    @Override
    public String getProcessSpecificDetails(HistoricProcessInstance instance, Map<String, Object> vars) {
        StringBuffer sb = new StringBuffer();
        sb.append("The following roles were requested to be added:\n");
        List<RoleType> roles = (List<RoleType>) vars.get(ROLES_TO_APPROVE);
        for (RoleType role : roles) {
            sb.append(" - " + role.getName() + " [" + role.getOid() + "]\n");
        }
        sb.append("\nDecisions done: ");
        DecisionList decisionList = (DecisionList) vars.get(DECISION_LIST);
        if (decisionList == null || decisionList.getDecisionList() == null) {
            sb.append("(error - DecisionList is null)\n");        // todo
        } else if (decisionList.getDecisionList().isEmpty()) {
            sb.append("none\n");
        } else {
            sb.append("\n");
            for (Decision decision : decisionList.getDecisionList()) {
                sb.append(" - role: " + decision.getRole() + ", approver: " + decision.getUser() + ", result: " + decision.isApproved() + ", comment: " + decision.getComment() + "\n");
            }
        }
        sb.append("\n");
        if (instance.getDeleteReason() != null) {
            sb.append("Reason for process deletion: ");
            sb.append(instance.getDeleteReason());
            sb.append("\n");
        }
        return sb.toString();
    }

}
