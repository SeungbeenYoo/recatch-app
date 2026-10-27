// 멤버십 바코드 번호 규칙. 웹 시연의 wallet-validation.js(PR #1)와 같은 규칙을 그대로 옮겼다.
// 멤버십 번호는 상품 GTIN이나 은행 카드번호가 아니므로, EAN-13·UPC-A를 고른 경우에만 검증 숫자를 본다.

export const FORMATS = {
  numeric: { label: '숫자형 바코드 (8~24자리)', min: 8, max: 24 },
  member16: { label: '16자리 멤버십·선불카드', min: 16, max: 16 },
  student12: { label: '12자리 학생증 (데모 예시)', min: 12, max: 12 },
  ean13: { label: 'EAN-13 (13자리·검증 숫자 포함)', min: 13, max: 13, gtin: true },
  upca: { label: 'UPC-A (12자리·검증 숫자 포함)', min: 12, max: 12, gtin: true }
};

export const normalize = value => String(value).replace(/[\s-]/g, '');

export function validGTIN(number) {
  const digits = number.slice(0, -1).split('').reverse();
  const sum = digits.reduce((total, digit, i) => total + Number(digit) * (i % 2 ? 1 : 3), 0);
  return (10 - sum % 10) % 10 === Number(number.at(-1));
}

/** @returns {{ raw: string } | { field: string, error: string }} */
export function validate({ name, number, format = 'numeric' }) {
  if (!name.trim() || name.trim().length > 40) return { field: 'name', error: '이름을 1~40자로 입력해주세요.' };
  if (!String(number).trim()) return { field: 'number', error: '바코드 번호를 입력해주세요.' };
  if (!/^[0-9\s-]+$/.test(number)) return { field: 'number', error: '숫자만 입력해주세요. 공백과 하이픈은 사용할 수 있어요.' };
  const raw = normalize(number), rule = FORMATS[format];
  if (!rule) return { field: 'format', error: '바코드 형식을 선택해주세요.' };
  if (raw.length < rule.min || raw.length > rule.max) return { field: 'number', error: rule.min === rule.max ? `${rule.min}자리 번호를 입력해주세요.` : `${rule.min}~${rule.max}자리 번호를 입력해주세요.` };
  if (/^(\d)\1+$/.test(raw)) return { field: 'number', error: '같은 숫자만 반복된 번호는 등록할 수 없어요.' };
  if (rule.gtin && !validGTIN(raw)) return { field: 'number', error: '바코드의 마지막 검증 숫자가 맞지 않아요. 번호를 다시 확인해주세요.' };
  return { raw };
}
