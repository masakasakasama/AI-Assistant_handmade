import { requestAuthError } from '../ai-backend/src/request-auth.mjs';
import { parseRequestBody,requestBodyError } from '../ai-backend/src/request-body.mjs';
import { createPairing,redeemPairing } from '../ai-backend/src/web-pairing.mjs';
import { logRequestFailure } from '../ai-backend/src/safe-logging.mjs';

export default async function handler(req,res){
  res.setHeader('Cache-Control','no-store');
  if(req.method!=='POST')return res.status(405).json({error:'method_not_allowed'});
  // Redemption authenticates with the short-lived one-use capability, not an owner token.
  if(req.query?.operation!=='redeem'){
    const auth=requestAuthError(req);if(auth)return res.status(auth.status).json(auth.body);
  }
  try{
    const body=parseRequestBody(req.body);
    if(req.query?.operation==='redeem')return res.status(200).json(await redeemPairing(body));
    return res.status(200).json(await createPairing(body));
  }catch(error){
    const invalid=requestBodyError(error);if(invalid)return res.status(invalid.status).json(invalid.body);
    if(error.status)return res.status(error.status).json({error:error.code});
    logRequestFailure();return res.status(503).json({error:'pairing_unavailable'});
  }
}
