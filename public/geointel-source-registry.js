export const GEO_SOURCES=Object.freeze({
 usgs:{name:'USGS Earthquake Hazards Program',coverage:'Séismes mondiaux M2,5+ ; fenêtre demandée',format:'json'},
 eonet:{name:'NASA EONET',coverage:'Événements naturels mondiaux signalés, non exhaustifs',format:'json'},
 nws:{name:'National Weather Service',coverage:'Alertes actives États-Unis et territoires ; pas une météo mondiale',format:'json'},
 ioda:{name:'IODA — Georgia Tech',coverage:'Anomalies réseau observées ; réseaux/pays, pas de localisation précise',format:'json'},
 un:{name:'ONU — paix et sécurité',coverage:'Publications ONU, pas un recensement exhaustif des conflits',format:'xml'},
 ofac:{name:'OFAC — actions récentes',coverage:'Publications américaines, pas une liste consolidée mondiale des sanctions',format:'xml'}
});
export function sourceUrl(id,days,now=Date.now()) {
 if(!Object.hasOwn(GEO_SOURCES,id)||![1,7,30].includes(days)||!Number.isFinite(now))throw Error('Unsupported source or range');
 const feeds={usgs:`https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_${{1:'day',7:'week',30:'month'}[days]}.geojson`,eonet:`https://eonet.gsfc.nasa.gov/api/v3/events?status=all&days=${days}&limit=500`,nws:'https://api.weather.gov/alerts/active?status=actual',ioda:`https://api.ioda.inetintel.cc.gatech.edu/v2/outages/events?from=${Math.floor(now/1000)-days*86400}&until=${Math.floor(now/1000)}&limit=50&extendWindow=0`,un:'https://news.un.org/feed/subscribe/en/news/topic/peace-and-security/feed/rss.xml',ofac:'https://ofac.treasury.gov/rss.xml'};
 return feeds[id];
}
