import fs from "fs";

const file = process.argv[2];
const d = JSON.parse(fs.readFileSync(file, "utf8"));

function walk(obj, visit, path = "") {
  if (!obj || typeof obj !== "object") return;
  if (Array.isArray(obj)) {
    obj.forEach((v, i) => walk(v, visit, `${path}[${i}]`));
    return;
  }
  visit(path, obj);
  for (const k of Object.keys(obj)) walk(obj[k], visit, path ? `${path}.${k}` : k);
}

const hits = [];
walk(d, (path, o) => {
  if (o.style && String(o.style).includes("VERIF")) hits.push({ path, style: o.style });
  if (o.iconType && String(o.iconType).includes("CHECK")) hits.push({ path, iconType: o.iconType });
  const label =
    o.accessibilityContext?.label ||
    o.accessibilityData?.label ||
    o.tooltip;
  if (label && /verif|official artist/i.test(String(label))) hits.push({ path, label });
});

console.log(JSON.stringify(hits.slice(0, 30), null, 2));
console.log("total hits", hits.length);
