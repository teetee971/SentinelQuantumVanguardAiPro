import axios from "axios";
export async function callGPT(prompt){
  const resp = await axios.post(
    "https://api.openai.com/v1/chat/completions",
    { model:"gpt-4o", messages:[{role:"system",content:"Tu es un assistant vocal empathique."},{role:"user",content:prompt}] },
    { headers:{Authorization:`Bearer ${process.env.OPENAI_API_KEY}`} }
  );
  const reply = resp.data.choices[0].message.content;
  const tone = reply.match(/calme|ferme|rassurant/i)?reply.match(/calme|ferme|rassurant/i)[0].toLowerCase():"neutre";
  return { reply, tone };
}
