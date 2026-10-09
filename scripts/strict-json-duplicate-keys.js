export function rejectDuplicateJsonKeys(text) {
  if (typeof text !== 'string') throw new TypeError('JSON input must be a string');
  let index = 0;

  const error = message => {
    throw new SyntaxError(`${message} at offset ${index}`);
  };
  const skipWhitespace = () => {
    while (index < text.length && /[\t\n\r ]/.test(text[index])) index += 1;
  };
  const parseString = () => {
    if (text[index] !== '"') error('expected string');
    const start = index;
    index += 1;
    while (index < text.length) {
      const char = text[index++];
      if (char === '"') return JSON.parse(text.slice(start, index));
      if (char === '\\') {
        if (index >= text.length) error('unterminated escape');
        const escape = text[index++];
        if (escape === 'u') {
          const code = text.slice(index, index + 4);
          if (!/^[0-9a-fA-F]{4}$/.test(code)) error('invalid unicode escape');
          index += 4;
        } else if (!'"\\/bfnrt'.includes(escape)) {
          error('invalid escape');
        }
      } else if (char.charCodeAt(0) < 0x20) {
        error('unescaped control character');
      }
    }
    error('unterminated string');
  };
  const parseNumber = () => {
    const match = /^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/.exec(text.slice(index));
    if (!match) error('invalid number');
    index += match[0].length;
  };
  const parseLiteral = literal => {
    if (!text.startsWith(literal, index)) error(`expected ${literal}`);
    index += literal.length;
  };
  const parseValue = () => {
    skipWhitespace();
    const char = text[index];
    if (char === '{') return parseObject();
    if (char === '[') return parseArray();
    if (char === '"') return void parseString();
    if (char === '-' || /[0-9]/.test(char || '')) return parseNumber();
    if (char === 't') return parseLiteral('true');
    if (char === 'f') return parseLiteral('false');
    if (char === 'n') return parseLiteral('null');
    error('expected JSON value');
  };
  const parseObject = () => {
    index += 1;
    skipWhitespace();
    const keys = new Set();
    if (text[index] === '}') {
      index += 1;
      return;
    }
    while (index < text.length) {
      skipWhitespace();
      const key = parseString();
      if (keys.has(key)) error(`duplicate object key ${JSON.stringify(key)}`);
      keys.add(key);
      skipWhitespace();
      if (text[index] !== ':') error('expected colon');
      index += 1;
      parseValue();
      skipWhitespace();
      if (text[index] === '}') {
        index += 1;
        return;
      }
      if (text[index] !== ',') error('expected comma or object end');
      index += 1;
    }
    error('unterminated object');
  };
  const parseArray = () => {
    index += 1;
    skipWhitespace();
    if (text[index] === ']') {
      index += 1;
      return;
    }
    while (index < text.length) {
      parseValue();
      skipWhitespace();
      if (text[index] === ']') {
        index += 1;
        return;
      }
      if (text[index] !== ',') error('expected comma or array end');
      index += 1;
    }
    error('unterminated array');
  };

  parseValue();
  skipWhitespace();
  if (index !== text.length) error('unexpected trailing content');
}
