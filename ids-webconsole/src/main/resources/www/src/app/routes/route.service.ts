import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import 'rxjs/add/operator/map';

import { Result, RouteResult } from '../result';
import { Route } from './route';
import { RouteMetrics } from './route-metrics';
import { ValidationInfo } from './validation';

import { environment } from '../../environments/environment';

@Injectable()
export class RouteService {
  constructor(private httpClient: HttpClient) { }

  getRoute(routeId: string): Observable<Route> {
    return this.httpClient.get(environment.apiURL + '/routes/get/' + routeId) as Observable<Route>;
  }

  getRouteAsString(routeId: string): Observable<string> {
    return this.httpClient.get(environment.apiURL + '/routes/getAsString/' + routeId, { responseType: 'text' }) as Observable<string>;
  }

  getValidationInfo(routeId: string): Observable<ValidationInfo> {
    return this.httpClient.get(environment.apiURL + '/routes/validate/' + routeId) as Observable<ValidationInfo>;
  }

  getMetrics(): Observable<RouteMetrics> {
    return this.httpClient.get(environment.apiURL + '/routes/metrics') as Observable<RouteMetrics>;
  }

  getRoutes(): Observable<Route[]> {
    return this.httpClient.get(environment.apiURL + '/routes/list/') as Observable<Route[]>;
  }

  stopRoute(routeId: string): Observable<Result> {
    // Stop Camel route
    return this.httpClient.get(environment.apiURL + '/routes/stoproute/' + routeId) as Observable<Result>;
  }

  startRoute(routeId: string): Observable<Result> {
    // Start Camel route
    return this.httpClient.get(environment.apiURL + '/routes/startroute/' + routeId) as Observable<Result>;
  }

  saveRoute(routeId: string, routeString: string): Observable<RouteResult> {
    // Update Camel route
    const headers = new HttpHeaders().set('Content-Type', 'text/plain; charset=utf-8');
    console.log('Sending Update: ' + routeString);
    return this.httpClient.post(environment.apiURL + '/routes/save/' + routeId,
      routeString, { headers: headers }) as Observable<RouteResult>;
  }

  addRoute(routeString: string): Observable<Result> {
    // Save new Camel route
    const headers = new HttpHeaders().set('Content-Type', 'text/plain; charset=utf-8');
    console.log('Sending New: ' + routeString);
    return this.httpClient.put(environment.apiURL + '/routes/add',
      routeString, { headers: headers }) as Observable<Result>;
  }

  listEndpoints(): Observable<string[]> {
    return this.httpClient.get(environment.apiURL + '/routes/list_endpoints') as Observable<string[]>;
  }

  listComponents(): Observable<string[]> {
    return this.httpClient.get(environment.apiURL + '/routes/list_components') as Observable<string[]>;
  }
}