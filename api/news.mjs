import { requestAuthError } from '../ai-backend/src/request-auth.mjs';
import { parseRequestBody, requestBodyError } from '../ai-backend/src/request-body.mjs';
import { publishNews, listNews, newsApiError } from '../ai-backend/src/news-store.mjs';
import { logRequestFailure } from '../ai-backend/src/safe-logging.mjs';

export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');
  if (!['GET','POST'].includes(req.method)) return res.status(405).json({ error: 'method_not_allowed' });
  const auth = requestAuthError(req);
  if (auth) return res.status(auth.status).json(auth.body);
  try {
    const result = req.method === 'POST'
      ? await publishNews(parseRequestBody(req.body))
      : await listNews();
    return res.status(200).json(result);
  } catch (error) {
    logRequestFailure();
    const failure = requestBodyError(error) || newsApiError(error);
    return res.status(failure.status).json(failure.body);
  }
}
