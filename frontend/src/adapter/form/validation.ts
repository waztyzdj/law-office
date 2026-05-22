import { z } from '../form';

export const passwordComplexityPattern =
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^\dA-Za-z]).{8,20}$/;
export const mobilePhonePattern = /^1[3-9]\d{9}$/;
export const telephonePattern = /^(?:\d{3,4}-?)?\d{7,8}$/;
export const idCardPattern = /(^\d{15}$)|(^\d{17}[\dXx]$)/;

export function optionalString<T extends z.ZodTypeAny>(schema: T) {
  return z.preprocess(
    (value) => (value === '' || value === null ? undefined : value),
    schema.optional(),
  );
}

export function passwordComplexityRule(
  message = '密码需为8-20位，包含大小写字母、数字和特殊字符',
) {
  return z.string().regex(passwordComplexityPattern, message);
}

export function confirmPasswordRule(
  values: Record<string, any>,
  passwordField = 'password',
) {
  return z
    .string({ required_error: '请再次输入登录密码' })
    .min(1, { message: '请再次输入登录密码' })
    .refine((value) => value === values[passwordField], {
      message: '两次输入的密码不一致',
    });
}

export function optionalMobilePhoneRule(message = '请输入正确的手机号码') {
  return optionalString(z.string().regex(mobilePhonePattern, message));
}

export function optionalTelephoneRule(message = '请输入正确的座机号码') {
  return optionalString(z.string().regex(telephonePattern, message));
}

export function optionalIdCardRule(message = '请输入正确的身份证号') {
  return optionalString(z.string().regex(idCardPattern, message));
}
