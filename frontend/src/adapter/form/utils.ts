type FormApiLike = {
  isMounted?: boolean;
  setValues: (values: Record<string, any>) => Promise<void>;
};

interface CleanFormPayloadOptions {
  id?: string;
  omit?: string[];
  removeEmptyString?: boolean;
  removeFalsyKeys?: string[];
}

interface ClearAutofillOptions {
  delays?: number[];
  enabled?: () => boolean;
}

export function noAutofillInputProps(
  name: string,
  props: Record<string, any> = {},
) {
  return {
    autocomplete: 'off',
    name,
    ...props,
  };
}

export function newPasswordInputProps(
  name: string,
  props: Record<string, any> = {},
) {
  return {
    autocomplete: 'new-password',
    name,
    ...props,
  };
}

export function clearAutofillValues(
  formApi: FormApiLike,
  values: Record<string, any>,
  options: ClearAutofillOptions = {},
) {
  const { delays = [0, 120], enabled } = options;

  delays.forEach((delay) => {
    setTimeout(() => {
      if (enabled && !enabled()) {
        return;
      }
      if (formApi.isMounted === false) {
        return;
      }

      void formApi.setValues({ ...values });
    }, delay);
  });
}

export function cleanFormPayload<T extends Record<string, any>>(
  values: Record<string, any>,
  options: CleanFormPayloadOptions = {},
) {
  const {
    id,
    omit = [],
    removeEmptyString = true,
    removeFalsyKeys = [],
  } = options;
  const payload: Record<string, any> = { ...values };

  if (id) {
    payload.id = id;
  }

  if (removeEmptyString) {
    for (const key of Object.keys(payload)) {
      if (payload[key] === '') {
        delete payload[key];
      }
    }
  }

  removeFalsyKeys.forEach((key) => {
    if (!payload[key]) {
      delete payload[key];
    }
  });

  omit.forEach((key) => {
    delete payload[key];
  });

  return payload as T;
}
