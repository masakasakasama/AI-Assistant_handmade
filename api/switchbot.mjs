import { requestAuthError } from '../ai-backend/src/request-auth.mjs';
import { parseRequestBody,requestBodyError } from '../ai-backend/src/request-body.mjs';
import { claimRequest,requestLimitFailure } from '../ai-backend/src/request-limits.mjs';
import { logRequestFailure } from '../ai-backend/src/safe-logging.mjs';
import { switchbotWeb } from '../ai-backend/src/switchbot-web.mjs';

export default async function handler(req,res) {
  res.setHeader('Cache-Control','no-store');
  if(req.method!=='POST')return res.status(405).json({error:'method_not_allowed'});
  const auth=requestAuthError(req);if(auth)return res.status(auth.status).json(auth.body);
  try {
    const body=parseRequestBody(req.body);
    await claimRequest();
    return res.status(200).json(await switchbotWeb(body));
  } catch(error) {
    const failure=requestBodyError(error)||requestLimitFailure(error);
    if(failure)return res.status(failure.status).json(failure.body);
    if(error.code==='invalid_switchbot_request')return res.status(400).json({error:error.code,message:error.message});
    logRequestFailure();
    return res.status(502).json({error:'switchbot_unavailable',message:'SwitchBotの受付を確認できませんでした。状態を確認してから再試行してください。'});
  }
}
