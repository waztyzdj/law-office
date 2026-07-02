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
        public static final String WITHDRAWN = "withdrawn";
        public static final String UNREAD = "unread";
        public static final String READ = "read";
        public static final String DELETED = "deleted";

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
        public static final String GATEWAY = "gateway";
        public static final String END = "end";

        private NodeType() {
        }
    }

    public static final class VirtualNode {
        public static final String START = "start";
        public static final String START_DRAFT = "start_draft";

        private VirtualNode() {
        }
    }

    public static final class VirtualNodeName {
        public static final String START_DRAFT = "提交申请";

        private VirtualNodeName() {
        }
    }

    public static final class TaskType {
        public static final String START_DRAFT = "start_draft";
        public static final String NORMAL = "normal";
        public static final String TRANSFER = "transfer";
        public static final String ADD_SIGN = "add_sign";
        public static final String COUNTERSIGN = "countersign";
        public static final String ORSIGN = "orsign";

        private TaskType() {
        }
    }

    public static final class ApprovalMode {
        public static final String SINGLE = "single";
        public static final String COUNTERSIGN = "countersign";
        public static final String ORSIGN = "orsign";

        private ApprovalMode() {
        }
    }

    public static final class AssigneeResolveMode {
        public static final String ALL = "all";
        public static final String SELECT = "select";

        private AssigneeResolveMode() {
        }
    }

    public static final class RejectPolicy {
        public static final String TERMINATE = "terminate";

        private RejectPolicy() {
        }
    }

    public static final class AssigneeType {
        public static final String USER = "user";
        public static final String ROLE = "role";
        public static final String DEPART_LEADER = "depart_leader";
        public static final String DEPART_ROLE = "depart_role";
        public static final String STARTER_SUPERVISOR = "starter_supervisor";
        public static final String STARTER_SELECT = "starter_select";
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
        public static final String WITHDRAW = "withdraw";
        public static final String CC = "cc";
        public static final String URGE = "urge";
        public static final String TIMEOUT_REMIND = "timeout_remind";
        public static final String TASK_CANCEL = "task_cancel";
        public static final String BRANCH_MATCH = "branch_match";
        public static final String ATTACHMENT_UPLOAD = "attachment_upload";
        public static final String ATTACHMENT_DELETE = "attachment_delete";
        public static final String SYSTEM_COMPLETE = "system_complete";

        private Action() {
        }
    }

    public static final class CcStatus {
        public static final String UNREAD = "unread";
        public static final String READ = "read";
        public static final String CANCELED = "canceled";

        private CcStatus() {
        }
    }

    public static final class CcTriggerAction {
        public static final String START = "start";
        public static final String APPROVE = "approve";
        public static final String PROCESS_FINISHED = "process_finished";
        public static final String MANUAL = "manual";

        private CcTriggerAction() {
        }
    }

    public static final class CcSourceType {
        public static final String RUNTIME_SELECT = "runtime_select";

        private CcSourceType() {
        }
    }

    public static final class RemindType {
        public static final String URGE = "urge";
        public static final String TIMEOUT = "timeout";

        private RemindType() {
        }
    }

    public static final class Reminder {
        public static final int URGE_INTERVAL_MINUTES = 10;

        private Reminder() {
        }
    }

    public static final class AttachmentStatus {
        public static final String ACTIVE = "active";
        public static final String DELETED = "deleted";

        private AttachmentStatus() {
        }
    }

    public static final class AttachmentSource {
        public static final String START = "start";
        public static final String TASK = "task";
        public static final String COMMENT = "comment";

        private AttachmentSource() {
        }
    }

    public static final class BusinessDocument {
        public static final String APPROVAL_BIZ_TYPE = "workflow_approval";

        private BusinessDocument() {
        }
    }

    public static final class BpmnSecurityStatus {
        public static final String PASSED = "passed";
        public static final String FAILED = "failed";

        private BpmnSecurityStatus() {
        }
    }

    public static final class AdminOperationType {
        public static final String REASSIGN = "reassign";
        public static final String TERMINATE = "terminate";
        public static final String RESEND_NOTICE = "resend_notice";

        private AdminOperationType() {
        }
    }

    public static final class AdminOperationStatus {
        public static final String SUCCESS = "success";
        public static final String FAILED = "failed";

        private AdminOperationStatus() {
        }
    }

    public static final class ArchiveSource {
        public static final String AUTO = "auto";
        public static final String MONITOR_MANUAL = "monitor_manual";
        public static final String ARCHIVE_MANUAL = "archive_manual";

        private ArchiveSource() {
        }
    }
}
