package com.lawoffice.workflow.constant;

/**
 * 审批中心稳定业务常量。
 */
public final class WorkflowConstants {

    private WorkflowConstants() {
    }

    public static final class Status {
        public static final String ENABLED = "enabled";
        public static final String DISABLED = "disabled";
        public static final String DRAFT = "draft";
        public static final String PUBLISHED = "published";
        public static final String ACTIVE = "active";
        public static final String ARCHIVED = "archived";
        public static final String RUNNING = "running";
        public static final String APPROVED = "approved";
        public static final String REJECTED = "rejected";
        public static final String TERMINATED = "terminated";
        public static final String TODO = "todo";
        public static final String DONE = "done";
        public static final String TRANSFERRED = "transferred";
        public static final String RETURNED = "returned";
        public static final String CANCELED = "canceled";
        public static final String CLAIMED = "claimed";

        private Status() {
        }
    }

    public static final class DesignerType {
        public static final String SIMPLE = "simple";
        public static final String BPMN = "bpmn";

        private DesignerType() {
        }
    }

    public static final class StartScopeType {
        public static final String ALL = "all";
        public static final String SPECIFIED = "specified";

        private StartScopeType() {
        }
    }

    public static final class TargetType {
        public static final String USER = "user";
        public static final String ROLE = "role";
        public static final String DEPART = "depart";
        public static final String TENANT = "tenant";

        private TargetType() {
        }
    }

    public static final class NodeType {
        public static final String START = "start";
        public static final String APPROVER = "approver";
        public static final String END = "end";

        private NodeType() {
        }
    }

    public static final class TaskType {
        public static final String START_DRAFT = "start_draft";
        public static final String NORMAL = "normal";
        public static final String TRANSFER = "transfer";
        public static final String ADD_SIGN = "add_sign";

        private TaskType() {
        }
    }

    public static final class AssigneeType {
        public static final String USER = "user";
        public static final String ROLE = "role";
        public static final String DEPART_LEADER = "depart_leader";
        public static final String DEPART_ROLE = "depart_role";
        public static final String STARTER = "starter";

        private AssigneeType() {
        }
    }

    public static final class FieldPermission {
        public static final String HIDDEN = "hidden";
        public static final String READONLY = "readonly";
        public static final String EDITABLE = "editable";

        private FieldPermission() {
        }
    }

    public static final class Action {
        public static final String SAVE_DRAFT = "save_draft";
        public static final String START = "start";
        public static final String APPROVE = "approve";
        public static final String REJECT = "reject";
        public static final String RETURN = "return";
        public static final String TRANSFER = "transfer";
        public static final String ADD_SIGN = "add_sign";
        public static final String SYSTEM_COMPLETE = "system_complete";

        private Action() {
        }
    }
}
