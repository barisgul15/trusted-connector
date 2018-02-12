import { Component, Input, Output, EventEmitter } from '@angular/core';

declare var API_URL: string;

@Component({
  selector: '[metricCard]',
  template: ` <div class="mdl-card mdl-shadow--2dp" style="min-height:10px!important;width:100%!important;min-width:20px!important">
               <div class="mdl-card__title mdl-card--expand" style="flex-direction:column;padding:12px">
                  <span style="font-size:30pt">{{value}}</span><br>
                  <span style="font-size:14pt">{{text}}</span>
               </div>
              </div>`,
})
export class MetricCardComponent {
  @Input('text') text = 'test';
  @Input('value') value = '0';
  @Output('valueChange') valueChange: EventEmitter<string> = new EventEmitter();

  private interval;

  constructor() { }
}
