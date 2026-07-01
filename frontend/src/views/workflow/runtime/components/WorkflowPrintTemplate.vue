<script setup lang="ts">
import type {
  InstanceDetailInfo,
  OperationRecordInfo,
  WorkflowAttachmentInfo,
} from '#/api/workflow';

import { computed } from 'vue';

import {
  getWorkflowActionMeta,
} from '../../components/status';

interface PrintField {
  span: number;
  key: string;
  label: string;
  value: string;
}

interface PrintRow {
  fields: PrintField[];
  key: string;
}

interface FormRuleLike {
  children?: FormRuleLike[];
  col?: Record<string, unknown>;
  display?: boolean;
  field?: string;
  hidden?: boolean;
  options?: unknown[];
  props?: Record<string, unknown>;
  title?: string;
  type?: string;
}

const props = withDefaults(
  defineProps<{
    attachments?: WorkflowAttachmentInfo[];
    detail?: InstanceDetailInfo;
  }>(),
  {
    attachments: () => [],
  },
);

const formInstance = computed(() => props.detail?.formInstance);
const formTitle = computed(() => formInstance.value?.formName || '审批单');
const formRows = computed(() => buildPrintRows());
const printTableRows = computed(() => splitRowsByGridSpan(formRows.value));
const PRINT_TABLE_COL_COUNT = 48;
const hiddenApprovalRecordActions = new Set(['branch_match', 'save_draft', 'start', 'urge']);
const approvalRecords = computed(() =>
  (props.detail?.records ?? []).filter(
    (record) => !hiddenApprovalRecordActions.has(String(record.action || '')),
  ),
);

function parseJson<T>(json: string | undefined, fallback: T): T {
  if (!json) {
    return fallback;
  }
  try {
    return JSON.parse(json) as T;
  } catch {
    return fallback;
  }
}

function buildPrintRows() {
  const rules = parseJson<FormRuleLike[]>(
    formInstance.value?.formSchemaSnapshotJson,
    [],
  );
  const formData = parseJson<Record<string, unknown>>(
    formInstance.value?.formDataJson,
    {},
  );
  return buildRowsFromRules(Array.isArray(rules) ? rules : [], formData);
}

function buildRowsFromRules(
  rules: FormRuleLike[],
  formData: Record<string, unknown>,
): PrintRow[] {
  const rows: PrintRow[] = [];
  let pendingFields: PrintField[] = [];
  let pendingSpan = 0;
  const flushPendingFields = () => {
    if (pendingFields.length === 0) {
      return;
    }
    rows.push({
      fields: pendingFields,
      key: `row-${rows.length}-${pendingFields.map((field) => field.key).join('-')}`,
    });
    pendingFields = [];
    pendingSpan = 0;
  };
  const appendField = (field: PrintField) => {
    if (field.span >= 24) {
      flushPendingFields();
      rows.push({
        fields: [field],
        key: field.key,
      });
      return;
    }
    if (pendingSpan + field.span > 24) {
      flushPendingFields();
    }
    pendingFields.push(field);
    pendingSpan += field.span;
    if (pendingSpan >= 24) {
      flushPendingFields();
    }
  };
  for (const rule of rules) {
    if (!isVisibleRule(rule)) {
      continue;
    }
    if (isRowRule(rule)) {
      flushPendingFields();
      const row = buildRowFromContainer(rule, formData, rows.length);
      if (row.fields.length > 0) {
        rows.push(row);
      }
      continue;
    }
    if (isColRule(rule)) {
      flushPendingFields();
      const fields = collectPrintableFields(
        rule.children ?? [],
        formData,
        resolveRuleSpan(rule, 24),
      );
      if (fields.length > 0) {
        rows.push({
          fields,
          key: `row-${rows.length}-${fields.map((field) => field.key).join('-')}`,
        });
      }
      continue;
    }
    if (rule.field) {
      const field = toPrintField(rule, formData, resolveRuleSpan(rule, 24));
      if (field) {
        appendField(field);
      }
      continue;
    }
    if (Array.isArray(rule.children) && rule.children.length > 0) {
      flushPendingFields();
      rows.push(...buildRowsFromRules(rule.children, formData));
    }
  }
  flushPendingFields();
  return rows;
}

function buildRowFromContainer(
  rowRule: FormRuleLike,
  formData: Record<string, unknown>,
  rowIndex: number,
): PrintRow {
  const fields: PrintField[] = [];
  for (const child of rowRule.children ?? []) {
    if (!isVisibleRule(child)) {
      continue;
    }
    if (isColRule(child)) {
      const colSpan = resolveRuleSpan(child, 24);
      const colFields = collectPrintableFields(child.children ?? [], formData, colSpan);
      fields.push(...colFields);
      continue;
    }
    if (child.field) {
      const field = toPrintField(child, formData, resolveRuleSpan(child, 24));
      if (field) {
        fields.push(field);
      }
      continue;
    }
    fields.push(...collectPrintableFields(child.children ?? [], formData, 24));
  }
  return {
    fields,
    key: rowRule.field || `row-${rowIndex}-${fields.map((field) => field.key).join('-')}`,
  };
}

function collectPrintableFields(
  rules: FormRuleLike[],
  formData: Record<string, unknown>,
  span: number,
): PrintField[] {
  const result: PrintField[] = [];
  for (const rule of rules) {
    if (!isVisibleRule(rule)) {
      continue;
    }
    if (rule.field) {
      const field = toPrintField(rule, formData, resolveRuleSpan(rule, span));
      if (field) {
        result.push(field);
      }
    }
    if (Array.isArray(rule.children)) {
      result.push(...collectPrintableFields(rule.children, formData, span));
    }
  }
  return result;
}

function isVisibleRule(rule: FormRuleLike) {
  return rule.hidden !== true && rule.display !== false;
}

function isRowRule(rule: FormRuleLike) {
  return ['fcrow', 'row'].includes(String(rule.type || '').toLowerCase());
}

function isColRule(rule: FormRuleLike) {
  return String(rule.type || '').toLowerCase() === 'col';
}

function normalizeSpan(value: unknown) {
  const span = Number(value);
  if (!Number.isFinite(span) || span <= 0) {
    return 24;
  }
  return Math.min(24, Math.max(1, Math.round(span)));
}

function resolveRuleSpan(rule: FormRuleLike, fallback: number) {
  return normalizeSpan(rule.col?.span ?? rule.props?.span ?? fallback);
}

function toPrintField(
  rule: FormRuleLike,
  formData: Record<string, unknown>,
  span: number,
): PrintField | undefined {
  if (!rule.field) {
    return undefined;
  }
  const label = rule.title || String(rule.props?.label || rule.field);
  return {
    key: rule.field,
    label,
    span: isFullRowRule(rule, formData[rule.field]) ? 24 : span,
    value: formatFieldValue(formData[rule.field], rule),
  };
}

function isFullRowRule(rule: FormRuleLike, value: unknown) {
  const type = String(rule.type || '').toLowerCase();
  if (isRangeValue(value)) {
    return false;
  }
  return (
    type.includes('textarea') ||
    type.includes('editor') ||
    type.includes('upload') ||
    type.includes('table') ||
    Array.isArray(value) ||
    (value !== null && typeof value === 'object')
  );
}

function formatFieldValue(value: unknown, rule: FormRuleLike): string {
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  const optionLabel = resolveOptionLabel(value, rule);
  if (optionLabel) {
    return optionLabel;
  }
  if (Array.isArray(value)) {
    if (isRangeValue(value)) {
      return value.map((item) => formatLooseValue(item)).join(' ～ ');
    }
    return value.length > 0 ? value.map((item) => formatLooseValue(item)).join('；') : '-';
  }
  return formatLooseValue(value);
}

function isRangeValue(value: unknown) {
  return (
    Array.isArray(value) &&
    value.length === 2 &&
    value.every((item) => isDateLikeValue(item))
  );
}

function isDateLikeValue(value: unknown) {
  if (typeof value !== 'string') {
    return false;
  }
  return /^\d{4}-\d{2}-\d{2}(?:[ T]\d{2}:\d{2}(?::\d{2})?)?$/.test(value.trim());
}

function formatLooseValue(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  if (typeof value === 'boolean') {
    return value ? '是' : '否';
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
}

function resolveOptionLabel(value: unknown, rule: FormRuleLike) {
  const options = collectOptions(rule);
  if (options.length === 0) {
    return '';
  }
  const values = Array.isArray(value) ? value : [value];
  const labels = values.map((item) => {
    const matched = options.find((option) => String(option.value) === String(item));
    return matched?.label || String(item);
  });
  return labels.join('、');
}

function collectOptions(rule: FormRuleLike) {
  const rawOptions = [
    ...(Array.isArray(rule.options) ? rule.options : []),
    ...(Array.isArray(rule.props?.options) ? rule.props.options : []),
  ];
  return rawOptions
    .map((item) => item as Record<string, unknown>)
    .map((item) => ({
      label: String(item.label ?? item.name ?? item.title ?? item.value ?? ''),
      value: item.value ?? item.id ?? item.key,
    }))
    .filter((item) => item.value !== undefined && item.label);
}

function formatAction(record: OperationRecordInfo) {
  return getWorkflowActionMeta(record.action).label;
}

function formatOperator(record: OperationRecordInfo) {
  return record.operatorRealname || record.operatorUsername || '-';
}

function formatApprovalNode(record: OperationRecordInfo) {
  return record.nodeName || (record.nodeId === '-' ? formatAction(record) : record.nodeId) || '-';
}

function formatApprovalText(record: OperationRecordInfo) {
  return record.comment || formatAction(record) || '-';
}

function formatApprovalSignature(record: OperationRecordInfo) {
  return [formatOperator(record), record.operateTime].filter(Boolean).join('  ');
}

function resolveFieldColspan(span: number) {
  return normalizeSpan(span) * 2;
}

function resolveLabelColspan(span: number) {
  const totalColspan = resolveFieldColspan(span);
  if (totalColspan <= 2) {
    return 1;
  }
  return Math.min(8, Math.max(2, Math.floor(totalColspan / 3)));
}

function resolveValueColspan(span: number) {
  return resolveFieldColspan(span) - resolveLabelColspan(span);
}

function resolveRowRemainderColspan(row: PrintRow) {
  const usedSpan = row.fields.reduce((sum, field) => sum + normalizeSpan(field.span), 0);
  return Math.max(0, (24 - Math.min(24, usedSpan)) * 2);
}

function splitRowsByGridSpan(rows: PrintRow[]) {
  const result: PrintRow[] = [];
  for (const row of rows) {
    let currentFields: PrintField[] = [];
    let currentSpan = 0;
    const flush = () => {
      if (currentFields.length === 0) {
        return;
      }
      result.push({
        fields: currentFields,
        key: `${row.key}-${result.length}`,
      });
      currentFields = [];
      currentSpan = 0;
    };
    for (const field of row.fields) {
      const span = normalizeSpan(field.span);
      if (currentSpan > 0 && currentSpan + span > 24) {
        flush();
      }
      currentFields.push({
        ...field,
        span,
      });
      currentSpan += span;
      if (currentSpan >= 24) {
        flush();
      }
    }
    flush();
  }
  return result;
}
</script>

<template>
  <article class="workflow-print-template">
    <h1 class="workflow-print-title">{{ formTitle }}</h1>

    <section class="workflow-print-section">
      <table
        v-if="printTableRows.length"
        class="workflow-print-fields"
      >
        <colgroup>
          <col
            v-for="index in PRINT_TABLE_COL_COUNT"
            :key="index"
          >
        </colgroup>
        <tbody>
        <tr
          v-for="row in printTableRows"
          :key="row.key"
          class="workflow-print-row"
        >
          <template
            v-for="field in row.fields"
            :key="field.key"
          >
            <th
              class="workflow-print-field__label"
              :colspan="resolveLabelColspan(field.span)"
            >
              {{ field.label }}
            </th>
            <td
              class="workflow-print-field__value"
              :colspan="resolveValueColspan(field.span)"
            >
              {{ field.value }}
            </td>
          </template>
          <td
            v-if="resolveRowRemainderColspan(row) > 0"
            class="workflow-print-field__value workflow-print-field__placeholder"
            :colspan="resolveRowRemainderColspan(row)"
          ></td>
        </tr>
        </tbody>
      </table>
      <div
        v-else
        class="workflow-print-empty"
      >
        暂无表单内容
      </div>
    </section>

    <section class="workflow-print-section workflow-print-approval-section">
      <div
        v-if="approvalRecords.length"
        class="workflow-print-approvals"
      >
        <div
          v-for="record in approvalRecords"
          :key="record.id || `${record.nodeId}-${record.operateTime}`"
          class="workflow-print-approval"
        >
          <div class="workflow-print-approval__node">
            {{ formatApprovalNode(record) }}：
          </div>
          <div class="workflow-print-approval__comment">
            {{ formatApprovalText(record) }}
          </div>
          <div class="workflow-print-approval__signature">
            {{ formatApprovalSignature(record) }}
          </div>
        </div>
      </div>
      <div
        v-else
        class="workflow-print-empty"
      >
        暂无审批记录
      </div>
    </section>

    <section class="workflow-print-section workflow-print-attachment-section">
      <div class="workflow-print-attachment-list">
        <div class="workflow-print-attachment-list__title">附件清单：</div>
        <template v-if="attachments.length">
          <div
            v-for="attachment in attachments"
            :key="attachment.id || attachment.fileId"
            class="workflow-print-attachment-list__item"
          >
            {{ attachment.fileName || attachment.fileId || '-' }}
          </div>
        </template>
        <div
          v-else
          class="workflow-print-attachment-list__empty"
        >
          无
        </div>
      </div>
    </section>
  </article>
</template>

<style scoped>
.workflow-print-template {
  background: #fff;
  color: #000;
  font-size: 12px;
  line-height: 1.7;
  margin: 0 auto;
  max-width: 794px;
  padding: 48px 59px 63px;
}

.workflow-print-title {
  color: #000;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.4;
  margin: 0 0 18px;
  text-align: center;
}

.workflow-print-section {
  margin-top: 18px;
}

.workflow-print-approval-section {
  margin-top: 0;
}

.workflow-print-attachment-section {
  margin-top: 0;
}

.workflow-print-fields {
  border-collapse: collapse;
  table-layout: fixed;
  width: 100%;
}

.workflow-print-field__label {
  background: #fafafa;
  border: 1px solid #8c8c8c;
  color: #1f2937;
  font-weight: 400;
  padding: 8px 10px;
  text-align: right;
  vertical-align: middle;
}

.workflow-print-field__value {
  border: 1px solid #8c8c8c;
  color: #000;
  padding: 8px 10px;
  vertical-align: middle;
  white-space: pre-wrap;
  word-break: break-word;
}

.workflow-print-approvals {
  border-left: 1px solid #8c8c8c;
}

.workflow-print-approval {
  border-bottom: 1px solid #8c8c8c;
  border-right: 1px solid #8c8c8c;
  min-height: 96px;
  padding: 10px 12px;
}

.workflow-print-approval__node {
  color: #000;
  font-weight: 600;
}

.workflow-print-approval__comment {
  color: #000;
  min-height: 40px;
  padding: 10px 0 6px 28px;
  white-space: pre-wrap;
  word-break: break-word;
}

.workflow-print-approval__signature {
  color: #111827;
  text-align: right;
  white-space: pre-wrap;
}

.workflow-print-attachment-list {
  color: #000;
  padding: 10px 12px 0;
}

.workflow-print-attachment-list__title {
  font-weight: 600;
  margin-bottom: 6px;
}

.workflow-print-attachment-list__item {
  line-height: 1.7;
  padding-left: 24px;
  word-break: break-word;
}

.workflow-print-attachment-list__empty {
  color: #6b7280;
  padding-left: 24px;
}

.workflow-print-empty {
  border: 1px solid #8c8c8c;
  color: #6b7280;
  padding: 16px;
  text-align: center;
}

@media print {
  .workflow-print-template {
    font-size: 12px !important;
    line-height: 1.75 !important;
    max-width: none !important;
    padding: 0 !important;
    width: auto !important;
  }

  .workflow-print-title {
    font-size: 22px !important;
  }

  .workflow-print-fields {
    border-collapse: collapse !important;
    table-layout: fixed !important;
    width: 100% !important;
  }

  .workflow-print-field__label {
    background: #fafafa !important;
    border: 1px solid #737373 !important;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }

  .workflow-print-field__value {
    border: 1px solid #737373 !important;
  }

  .workflow-print-approvals {
    border-left: 1px solid #737373 !important;
  }

  .workflow-print-approval {
    border-bottom: 1px solid #737373 !important;
    border-right: 1px solid #737373 !important;
  }

  .workflow-print-field__label,
  .workflow-print-field__value {
    padding: 2.5mm 3mm;
  }

  .workflow-print-approval,
  .workflow-print-row {
    break-inside: avoid;
    page-break-inside: avoid;
  }
}
</style>
